package app.fixd.messaging.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads SMS/MMS data from the system Telephony provider. Requires READ_SMS. */
class MessageRepository(private val context: Context) {

    suspend fun loadConversations(limit: Int = 200): List<Conversation> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Conversation>()
        val uri = Uri.parse("content://mms-sms/conversations?simple=true")
        val proj = arrayOf("_id", "recipient_ids", "snippet", "date", "message_count", "read")
        runCatching {
            context.contentResolver.query(uri, null, null, null, "date DESC LIMIT $limit")?.use { c ->
                val idIdx = c.getColumnIndex("_id").coerceAtLeast(c.getColumnIndex(Telephony.Threads._ID))
                val snippetIdx = c.getColumnIndex("snippet")
                val dateIdx = c.getColumnIndex("date")
                val countIdx = c.getColumnIndex("message_count")
                val readIdx = c.getColumnIndex("read")
                val recipIdx = c.getColumnIndex("recipient_ids")
                while (c.moveToNext()) {
                    val tid = if (idIdx >= 0) c.getLong(idIdx) else continue
                    val snippet = if (snippetIdx >= 0) c.getString(snippetIdx).orEmpty() else ""
                    val date = if (dateIdx >= 0) c.getLong(dateIdx) else 0L
                    val count = if (countIdx >= 0) c.getInt(countIdx) else 0
                    val read = if (readIdx >= 0) c.getInt(readIdx) == 1 else true
                    val recipientIds = if (recipIdx >= 0) c.getString(recipIdx).orEmpty() else ""
                    val address = resolveAddress(recipientIds)
                    out.add(Conversation(
                        threadId = tid,
                        address = address,
                        displayName = null,
                        snippet = snippet,
                        date = date,
                        unreadCount = if (read) 0 else 1,
                        messageCount = count,
                        hasMms = false
                    ))
                }
            }
        }
        out
    }

    private fun resolveAddress(recipientIds: String): String {
        if (recipientIds.isBlank()) return ""
        val firstId = recipientIds.split(" ").firstOrNull() ?: return ""
        val canonical = ContentUris.withAppendedId(Uri.parse("content://mms-sms/canonical-address"), firstId.toLongOrNull() ?: return "")
        return runCatching {
            context.contentResolver.query(canonical, null, null, null, null)?.use { cur ->
                if (cur.moveToFirst()) cur.getString(0).orEmpty() else ""
            }.orEmpty()
        }.getOrDefault("")
    }

    suspend fun loadMessages(threadId: Long): List<Message> = withContext(Dispatchers.IO) {
        val out = mutableListOf<Message>()
        val uri = Telephony.Sms.CONTENT_URI
        val proj = arrayOf(
            Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ
        )
        runCatching {
            context.contentResolver.query(uri, proj, "${Telephony.Sms.THREAD_ID}=?", arrayOf(threadId.toString()), "${Telephony.Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val type = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.TYPE))
                    out.add(Message(
                        id = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms._ID)),
                        threadId = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)),
                        address = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)),
                        body = c.getString(c.getColumnIndexOrThrow(Telephony.Sms.BODY)).orEmpty(),
                        date = c.getLong(c.getColumnIndexOrThrow(Telephony.Sms.DATE)),
                        isIncoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        isMms = false,
                        read = c.getInt(c.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1
                    ))
                }
            }
        }
        out
    }

    suspend fun markThreadRead(threadId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            val values = android.content.ContentValues().apply { put(Telephony.Sms.READ, 1) }
            context.contentResolver.update(Telephony.Sms.CONTENT_URI, values, "${Telephony.Sms.THREAD_ID}=? AND ${Telephony.Sms.READ}=0", arrayOf(threadId.toString()))
        }
    }
}
