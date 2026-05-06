package app.fixd.messaging.sms

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import app.fixd.messaging.FixdApp
import app.fixd.messaging.MainActivity
import app.fixd.messaging.R

/** Receives incoming SMS broadcasts (SMS_DELIVER_ACTION) when we are the default app. */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return
        val from = msgs[0].originatingAddress.orEmpty()
        val body = msgs.joinToString(separator = "") { it.messageBody.orEmpty() }
        val ts = msgs[0].timestampMillis

        // Persist to Inbox - required behavior of default SMS app
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, from)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, ts)
            put(Telephony.Sms.DATE_SENT, ts)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
        }
        runCatching { ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }

        // Notify
        val tapIntent = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = android.app.PendingIntent.getActivity(ctx, 0, tapIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE)
        val notif = NotificationCompat.Builder(ctx, FixdApp.CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(from.ifBlank { "New message" })
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(from.hashCode(), notif) }
    }
}
