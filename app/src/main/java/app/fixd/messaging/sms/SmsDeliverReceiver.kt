package app.fixd.messaging.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/**
 * Receives inbound SMS while the app is the user's default SMS handler.
 * Persists each message to the system inbox so other apps (and our UI) can
 * read it via the Telephony provider.
 */
class SmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        for (m in msgs) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, m.originatingAddress)
                put(Telephony.Sms.BODY, m.messageBody)
                put(Telephony.Sms.DATE, m.timestampMillis)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.SEEN, 0)
            }
            ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        }
    }
}
