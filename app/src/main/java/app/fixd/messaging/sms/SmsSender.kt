package app.fixd.messaging.sms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SmsSender {
    suspend fun send(ctx: Context, address: String, body: String): Long = withContext(Dispatchers.IO) {
        val sm: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ctx.getSystemService(SmsManager::class.java)
        } else {
            @Suppress("DEPRECATION") SmsManager.getDefault()
        }
        val parts = sm.divideMessage(body)
        val sentPI = ArrayList<PendingIntent>()
        val deliveredPI = ArrayList<PendingIntent>()
        repeat(parts.size) {
            sentPI.add(PendingIntent.getBroadcast(ctx, 0, Intent("app.fixd.messaging.SMS_SENT"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
            deliveredPI.add(PendingIntent.getBroadcast(ctx, 0, Intent("app.fixd.messaging.SMS_DELIVERED"),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
        }
        runCatching {
            sm.sendMultipartTextMessage(address, null, parts, sentPI, deliveredPI)
        }
        // Persist to Sent
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
}
