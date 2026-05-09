package app.fixd.messaging.data

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val hasMms: Boolean,
    val isGroup: Boolean = false,
    val recipientIds: String = ""
)
