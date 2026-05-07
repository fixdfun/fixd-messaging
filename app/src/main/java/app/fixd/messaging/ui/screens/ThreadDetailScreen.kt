package app.fixd.messaging.ui.screens
import app.fixd.messaging.data.Attachment
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import android.net.Uri
import android.graphics.BitmapFactory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.fixd.messaging.data.Message
import app.fixd.messaging.data.ContactResolver
import app.fixd.messaging.data.MessageRepository
import app.fixd.messaging.sms.SmsSender
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(threadId: Long, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { MessageRepository(ctx) }
    val scope = rememberCoroutineScope()
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    suspend fun reload() {
        messages = repo.loadMessages(threadId)
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
        repo.markThreadRead(threadId)
    }

    LaunchedEffect(threadId) { reload() }

    val recipient = messages.firstOrNull()?.address ?: ""

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipient.ifBlank { "Conversation" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                Spacer(Modifier.width(4.dp))
                FilledIconButton(onClick = {
                    val toSend = input.trim()
                    if (toSend.isNotBlank() && recipient.isNotBlank()) {
                        scope.launch {
                            SmsSender.send(ctx, recipient, toSend)
                            input = ""
                            reload()
                        }
                    }
                }) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No messages yet", textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg -> MessageBubble(msg) }
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: Message) {
    val incoming = msg.isIncoming
    val bg = if (incoming) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primary
    val fg = if (incoming) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onPrimary
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bg)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            if (msg.attachments.isNotEmpty()) {
                msg.attachments.forEach { att -> AttachmentView(att, fg) }
            }
            if (msg.body.isNotEmpty()) Text(msg.body, color = fg)
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(msg.date)),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.7f)
            )
        }
    }
}

@androidx.compose.runtime.Composable
private fun AttachmentView(att: Attachment, fg: androidx.compose.ui.graphics.Color) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val isImage = att.contentType.startsWith("image/")
    val isVideo = att.contentType.startsWith("video/")
    if (isImage) {
        val bmp = androidx.compose.runtime.remember(att.partId) {
            runCatching {
                ctx.contentResolver.openInputStream(android.net.Uri.parse("content://mms/part/${att.partId}"))?.use {
                    android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (bmp != null) {
            androidx.compose.foundation.Image(
                bitmap = bmp,
                contentDescription = att.filename,
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit,
            )
        } else {
            androidx.compose.material3.Text("[image]", color = fg)
        }
    } else if (isVideo) {
        val bmp = androidx.compose.runtime.remember(att.partId) {
            runCatching {
                val tmp = java.io.File(ctx.cacheDir, "vthumb_${att.partId}.bin")
                if (!tmp.exists()) {
                    ctx.contentResolver.openInputStream(android.net.Uri.parse("content://mms/part/${att.partId}"))?.use { input ->
                        tmp.outputStream().use { out -> input.copyTo(out) }
                    }
                }
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    mmr.setDataSource(tmp.absolutePath)
                    mmr.getFrameAtTime(0)?.asImageBitmap()
                } finally {
                    runCatching { mmr.release() }
                }
            }.getOrNull()
        }
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .heightIn(max = 220.dp)
                .clickable {
                    val uri = android.net.Uri.parse("content://mms/part/${att.partId}")
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, att.contentType)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { ctx.startActivity(android.content.Intent.createChooser(intent, "Open video")) }
                }
        ) {
            if (bmp != null) {
                androidx.compose.foundation.Image(
                    bitmap = bmp,
                    contentDescription = att.filename,
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                )
                androidx.compose.material3.Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "video",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.Center).size(48.dp),
                )
            } else {
                androidx.compose.material3.Text("[video]", color = fg)
            }
        }
    } else if (!att.text.isNullOrEmpty()) {
        androidx.compose.material3.Text(att.text!!, color = fg)
    } else {
        androidx.compose.material3.Text("[" + (att.filename ?: att.contentType) + "]", color = fg)
    }
}
