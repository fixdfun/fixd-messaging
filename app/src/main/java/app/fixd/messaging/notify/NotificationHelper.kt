package app.fixd.messaging.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.fixd.messaging.MainActivity

object NotificationHelper {
    private const val CHANNEL_ID = "fixd_messages"
    private const val CHANNEL_NAME = "Messages"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "New SMS and MMS messages"
                    enableLights(true)
                    enableVibration(true)
                }
                nm.createNotificationChannel(ch)
            }
        }
    }

    fun showIncoming(ctx: Context, sender: String, body: String, threadId: Long) {
        ensureChannel(ctx)
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("open_thread_id", threadId)
        }
        val pi = PendingIntent.getActivity(ctx, threadId.toInt().coerceAtLeast(0), open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle(sender)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = ContextCompat.getSystemService(ctx, NotificationManager::class.java) ?: return
        nm.notify(threadId.toInt().coerceAtLeast(0), n)
    }
}
