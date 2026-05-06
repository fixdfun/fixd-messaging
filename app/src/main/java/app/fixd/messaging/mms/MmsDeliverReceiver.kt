package app.fixd.messaging.mms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * WAP-PUSH receiver for inbound MMS while we are the default SMS app. The
 * payload is a binary PDU; download + parsing happens in MmsDownloader.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val pdu = intent.getByteArrayExtra("data")
        Log.i("FixdMms", "WAP-PUSH delivered, ${pdu?.size ?: 0} bytes")
        // TODO: parse notification PDU, schedule MMS download via SmsManager.downloadMultimediaMessage
    }
}
