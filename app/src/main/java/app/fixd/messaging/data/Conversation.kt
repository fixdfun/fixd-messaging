package app.fixd.messaging.data

import android.content.Context
import android.provider.Telephony

data class Conversation(
    val threadId: Long,
    val address: String,
    val snippet: String,
    val date: Long
)

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean
)

object ConversationsRepository {
    fun load(ctx: Context): List<Conversation> {
        val out = mutableListOf<Conversation>()
        val cr = ctx.contentResolver
        val proj = arrayOf(
            Telephony.Threads._ID,
            Telephony.Threads.SNIPPET,
            Telephony.Threads.DATE,
            Telephony.Threads.RECIPIENT_IDS
        )
        cr.query(Telephony.Threads.CONTENT_URI, proj, null, null, "${Telephony.Threads.DATE} DESC")?.use { c ->
            while (c.moveToNext()) {
                out.add(
                    Conversation(
                        threadId = c.getLong(0),
                        address = c.getString(3) ?: "",
                        snippet = c.getString(1) ?: "",
                        date = c.getLong(2)
                    )
                )
            }
        }
        return out
    }
}
