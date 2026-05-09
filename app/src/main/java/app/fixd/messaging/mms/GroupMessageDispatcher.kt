package app.fixd.messaging.mms

import android.content.Context
import android.util.Log
import app.fixd.messaging.data.GroupConversation
import app.fixd.messaging.data.GroupRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "GroupDispatcher"

/**
 * Routes outbound group messages.
 *
 * A group message is sent as a single MMS with all member addresses in the To
 * field (group MMS). The carrier's MMSC delivers it to each recipient; reply-all
 * behaviour is carrier-dependent but most modern carriers support it.
 *
 * Attachment support: pass [mediaParts] for images/video/audio. For a text-only
 * group message pass an empty list  the text will be wrapped in a text/plain part.
 */
object GroupMessageDispatcher {

    /**
     * Send [text] (and optional [mediaParts]) to all members of [group].
     * Updates the group's threadId in [GroupRepository] after the first send.
     *
     * @return true if MMS dispatch succeeded
     */
    suspend fun send(
        ctx: Context,
        group: GroupConversation,
        text: String,
        mediaParts: List<MmsPart> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        if (group.members.isEmpty()) {
            Log.w(TAG, "Cannot send to empty group '${group.name}'")
            return@withContext false
        }

        val parts = buildParts(text, mediaParts)

        Log.i(TAG, "Sending group MMS to ${group.members.size} members in '${group.name}'")
        val success = MmsSender.send(
            ctx = ctx,
            recipients = group.members,
            subject = group.name,
            parts = parts
        )

        if (success) {
            // Find the newly created thread and associate it with the group
            val threadId = resolveGroupThread(ctx, group.members)
            if (threadId != null && threadId != group.threadId) {
                GroupRepository(ctx).updateThreadId(group.groupId, threadId)
                Log.i(TAG, "Group '${group.name}' threadId updated to $threadId")
            }
        }

        success
    }

    /**
     * Send [text] + optional [mediaParts] to an ad-hoc list of [recipients]
     * without a named group (used from ComposeScreen multi-recipient flow).
     */
    suspend fun sendAdHoc(
        ctx: Context,
        recipients: List<String>,
        text: String,
        mediaParts: List<MmsPart> = emptyList()
    ): Boolean = withContext(Dispatchers.IO) {
        if (recipients.isEmpty()) return@withContext false
        val parts = buildParts(text, mediaParts)
        MmsSender.send(ctx, recipients, subject = null, parts = parts)
    }

    //  Helpers 

    private fun buildParts(text: String, extra: List<MmsPart>): List<MmsPart> {
        val result = mutableListOf<MmsPart>()
        if (text.isNotBlank()) {
            result.add(MmsPart("text/plain", text.toByteArray(Charsets.UTF_8), "message.txt"))
        }
        result.addAll(extra)
        return result
    }

    /**
     * After sending a group MMS, find its thread ID in the Telephony provider.
     * We query the outbox sorted by date DESC to get the most recent thread.
     */
    private fun resolveGroupThread(ctx: Context, members: List<String>): Long? {
        return runCatching {
            ctx.contentResolver.query(
                android.provider.Telephony.Mms.Outbox.CONTENT_URI,
                arrayOf(android.provider.Telephony.Mms.THREAD_ID),
                null, null,
                "${android.provider.Telephony.Mms.DATE} DESC LIMIT 1"
            )?.use { c ->
                if (c.moveToFirst()) c.getLong(0) else null
            }
        }.getOrNull()
    }
}
