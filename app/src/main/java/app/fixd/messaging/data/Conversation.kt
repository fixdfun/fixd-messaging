package app.fixd.messaging.data

data class Conversation(
    val threadId: Long,
    val address: String,
    val displayName: String?,
    val snippet: String,
    val date: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val hasMms: Boolean
)

data class Message(
    val id: Long,
    val threadId: Long,
    val address: String?,
    val body: String,
    val date: Long,
    val isIncoming: Boolean,
    val isMms: Boolean,
    val read: Boolean,
    val attachments: List<Attachment> = emptyList()
)

data class Attachment(
    val partId: Long,
    val contentType: String,
    val filename: String?,
    val text: String?
)
