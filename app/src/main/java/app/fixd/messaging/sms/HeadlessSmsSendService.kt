package app.fixd.messaging.sms

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.os.Build

/** Required by default SMS apps - allows other apps to send SMS via RESPOND_VIA_MESSAGE. */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val msg = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val to = intent?.dataString?.removePrefix("smsto:")?.removePrefix("sms:")
        if (!msg.isNullOrBlank() && !to.isNullOrBlank()) {
            val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                getSystemService(SmsManager::class.java)
            else @Suppress("DEPRECATION") SmsManager.getDefault()
            runCatching { sm.sendTextMessage(to, null, msg, null, null) }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }
}
