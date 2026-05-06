package app.fixd.messaging

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.emoji2.bundled.BundledEmojiCompatConfig
import androidx.emoji2.text.EmojiCompat

class FixdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EmojiCompat.init(BundledEmojiCompatConfig(this))
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val incoming = NotificationChannel(
                CHANNEL_INCOMING,
                "Incoming messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = "New SMS and MMS messages" }
            val send = NotificationChannel(
                CHANNEL_SEND_STATUS,
                "Send status",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Outgoing message delivery status" }
            nm.createNotificationChannels(listOf(incoming, send))
        }
    }

    companion object {
        const val CHANNEL_INCOMING = "fixd.incoming"
        const val CHANNEL_SEND_STATUS = "fixd.send"
    }
}
