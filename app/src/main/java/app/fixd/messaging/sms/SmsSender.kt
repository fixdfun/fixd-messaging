package app.fixd.messaging.sms

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import android.telephony.SmsManager

object SmsSender {
    /**
     * Sends an SMS via the platform [SmsManager] and writes a copy into the
     * system Telephony.Sms.Sent provider so the conversation thread shows it.
     */
    fun send(ctx: Context, address: String, body: String) {
        if (address.isBlank() || body.isBlank()) return
        val sm = ctx.getSystemService(SmsManager::class.java)
        val parts = sm.divideMessage(body)
        if (parts.size == 1) sm.sendTextMessage(address, null, body, null, null)
        else sm.sendMultipartTextMessage(address, null, parts, null, null)

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        ctx.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }
}
