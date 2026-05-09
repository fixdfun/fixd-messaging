package app.fixd.messaging.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import app.fixd.messaging.crypto.FixdSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "SmsSender"

object SmsSender {

    /**
     * Send [body] to [address].
     *
     * If the recipient has an established Fixd session and the user has opted
     * into E2E encryption for this conversation, the body is encrypted using
     * the Signal Double-Ratchet and sent as one or more FXD1:M: segments.
     * The plaintext is always what gets persisted to the Sent mailbox so the
     * user sees their own message in readable form.
     *
     * @return rowId of the inserted Sent record, or -1 on failure.
     */
    suspend fun send(ctx: Context, address: String, body: String): Long =
        withContext(Dispatchers.IO) {
            val signal = FixdSignal(ctx)

            // Determine the wire payloads (encrypted segments or plain body)
            val wireMessages: List<String> = if (signal.shouldEncrypt(address)) {
                runCatching { signal.encryptMessage(address, body) }
                    .getOrElse {
                        Log.e(TAG, "Encryption failed, falling back to plaintext: ${it.message}")
                        listOf(body)
                    }
            } else {
                listOf(body)
            }

            val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            // Send each wire segment (may be multiple for encrypted multi-part)
            wireMessages.forEach { wireBody ->
                val parts = sm.divideMessage(wireBody)
                val sentPI = ArrayList<PendingIntent>()
                val deliveredPI = ArrayList<PendingIntent>()
                repeat(parts.size) {
                    sentPI.add(
                        PendingIntent.getBroadcast(
                            ctx, 0,
                            Intent("app.fixd.messaging.SMS_SENT"),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                    deliveredPI.add(
                        PendingIntent.getBroadcast(
                            ctx, 0,
                            Intent("app.fixd.messaging.SMS_DELIVERED"),
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )
                    )
                }
                runCatching {
                    sm.sendMultipartTextMessage(address, null, parts, sentPI, deliveredPI)
                }.onFailure { Log.e(TAG, "SmsManager send failed: ${it.message}") }
            }

            // Always persist the PLAINTEXT body to Sent (user-readable)
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, System.currentTimeMillis())
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.SEEN, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            val uri = ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            uri?.lastPathSegment?.toLongOrNull() ?: -1L
        }

    /**
     * Send a handshake (PreKeyBundle) to [address] when the user first opts in
     * to E2E encryption for this conversation.
     * The handshake segments are sent as SMS but not stored in the Sent box.
     */
    suspend fun sendHandshake(ctx: Context, address: String) =
        withContext(Dispatchers.IO) {
            val signal = FixdSignal(ctx)
            val segments = runCatching { signal.initiateHandshake(address) }.getOrNull()
                ?: return@withContext

            val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                ctx.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            segments.forEach { segment ->
                val parts = sm.divideMessage(segment)
                runCatching {
                    sm.sendMultipartTextMessage(address, null, parts, null, null)
                }.onFailure { Log.e(TAG, "Handshake send failed: ${it.message}") }
            }
            Log.i(TAG, "Handshake sent to $address (${segments.size} segment(s))")
        }
}
