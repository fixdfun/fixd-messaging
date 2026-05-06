package app.fixd.messaging.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony

/** Receives WAP_PUSH_DELIVER for incoming MMS. Full PDU parsing is a TODO. */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        // TODO: parse application/vnd.wap.mms-message, fetch via SmsManager.downloadMultimediaMessage
        // For now we are a passive receiver to remain a valid default-SMS app.
    }
}
