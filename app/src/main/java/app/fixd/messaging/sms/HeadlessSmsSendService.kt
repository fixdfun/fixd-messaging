package app.fixd.messaging.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Required for the app to be eligible as the default SMS handler. The OS may
 * launch this service to send a quick reply when the user takes an action
 * like "Respond via message" without opening the app.
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val to = intent.data?.schemeSpecificPart.orEmpty()
            val body = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            if (to.isNotBlank() && body.isNotBlank()) SmsSender.send(this, to, body)
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
