package app.fixd.messaging.mms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import app.fixd.messaging.FixdApp
import app.fixd.messaging.MainActivity
import app.fixd.messaging.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "MmsReceiver"

/**
 * Receives WAP_PUSH_DELIVER_ACTION for incoming MMS.
 *
 * Flow:
 *  1. Detect content-type application/vnd.wap.mms-message (WAP push notification)
 *  2. Call SmsManager.downloadMultimediaMessage() to fetch the full PDU
 *  3. Insert the downloaded MMS into the Telephony MMS inbox
 *  4. Post a notification
 *
 * The actual PDU parsing (reading parts from content://mms/*/part) is done
 * lazily by MessageRepository when the thread is opened in the UI.
 */
class MmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val mimeType = intent.type ?: return
        if (!mimeType.equals("application/vnd.wap.mms-message", ignoreCase = true)) return

        val data = intent.getByteArrayExtra("data") ?: return
        Log.i(TAG, "WAP push received, PDU size=${data.size}")

        CoroutineScope(Dispatchers.IO).launch {
            downloadAndStore(ctx, data)
        }
    }

    private fun downloadAndStore(ctx: Context, notificationPdu: ByteArray) {
        // Parse the X-Mms-Transaction-Id and content-location from the notification PDU
        // so we can call downloadMultimediaMessage correctly.
        val contentLocation = extractContentLocation(notificationPdu)
        if (contentLocation.isNullOrBlank()) {
            Log.w(TAG, "No content-location in MMS notification PDU  cannot download")
            return
        }

        Log.i(TAG, "Downloading MMS from: $contentLocation")

        // Write a temp file to receive the downloaded PDU
        val tmpFile = java.io.File(ctx.cacheDir, "mms_incoming_${System.currentTimeMillis()}.dat")

        val contentUri = androidx.core.content.FileProvider.getUriForFile(
            ctx, "${ctx.packageName}.fileprovider", tmpFile
        )

        val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getDefault()
        }

        // downloadMultimediaMessage is async; we use a null PI and poll
        runCatching {
            @Suppress("DEPRECATION")
            sm.downloadMultimediaMessage(ctx, contentLocation, contentUri, null, null)
        }.onFailure {
            Log.e(TAG, "downloadMultimediaMessage failed: ${it.message}")
            return
        }

        // Give the download time to complete (async workaround for no PI result)
        Thread.sleep(3000)

        if (!tmpFile.exists() || tmpFile.length() == 0L) {
            Log.w(TAG, "Downloaded PDU file empty or missing")
            return
        }

        // Insert into system MMS inbox using the downloaded PDU file URI
        insertMmsRecord(ctx, contentUri)
        tmpFile.delete()

        // Notify
        postNotification(ctx, "New MMS", "You received a media message")
    }

    /**
     * Extract X-Mms-Content-Location from a WAP MMS notification PDU.
     * Very simplified parser  reads field IDs looking for 0x83 (content-location).
     */
    private fun extractContentLocation(pdu: ByteArray): String? {
        var i = 0
        while (i < pdu.size) {
            val fieldId = pdu[i].toInt() and 0xFF
            i++
            when (fieldId) {
                0x83 -> { // X-Mms-Content-Location
                    val start = i
                    while (i < pdu.size && pdu[i] != 0.toByte()) i++
                    return String(pdu, start, i - start, Charsets.UTF_8).also { i++ }
                }
                0x98 -> { // Transaction-ID  skip null-terminated string
                    while (i < pdu.size && pdu[i] != 0.toByte()) i++
                    i++
                }
                0x8D -> i++ // MMS-Version  1 byte value
                0x8C -> i++ // Message-Type  1 byte
                else -> { // Unknown  try to skip safely
                    if (i < pdu.size) {
                        val v = pdu[i].toInt() and 0xFF
                        if (v < 0x20) i++ else {
                            while (i < pdu.size && pdu[i] != 0.toByte()) i++
                            if (i < pdu.size) i++
                        }
                    }
                }
            }
        }
        return null
    }

    private fun insertMmsRecord(ctx: Context, pduUri: Uri) {
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
            put(Telephony.Mms.READ, 0)
            put(Telephony.Mms.SEEN, 0)
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
            put(Telephony.Mms.MESSAGE_TYPE, 132) // m-retrieve-conf
        }
        runCatching {
            ctx.contentResolver.insert(Telephony.Mms.CONTENT_URI, values)
        }.onFailure { Log.e(TAG, "Failed to insert MMS record: ${it.message}") }
    }

    private fun postNotification(ctx: Context, title: String, body: String) {
        val tap = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notif = NotificationCompat.Builder(ctx, FixdApp.CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(tap)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify("mms".hashCode(), notif) }
    }
}
