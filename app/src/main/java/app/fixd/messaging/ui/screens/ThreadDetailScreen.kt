package app.fixd.messaging.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.fixd.messaging.crypto.FixdSignal
import app.fixd.messaging.crypto.PeerRegistry
import app.fixd.messaging.data.Attachment
import app.fixd.messaging.data.Message
import app.fixd.messaging.data.MessageRepository
import app.fixd.messaging.sms.SmsSender
import app.fixd.messaging.ui.EncryptionBadge
import app.fixd.messaging.ui.MessageEncryptedIcon
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(
    threadId: Long,
    onBack: () -> Unit,
    onSafetyNumber: (address: String) -> Unit = {}
) {
    val ctx = LocalContext.current
    val repo = remember { MessageRepository(ctx) }
    val scope = rememberCoroutineScope()

    var messages   by remember { mutableStateOf<List<Message>>(emptyList()) }
    var input      by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    val listState  = rememberLazyListState()

    // E2E state
    var isOptedIn   by remember { mutableStateOf(false) }
    var isEncrypted by remember { mutableStateOf(false) }

    suspend fun reload() {
        messages = repo.loadMessages(threadId)
        if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
        repo.markThreadRead(threadId)
    }

    LaunchedEffect(threadId) { reload() }

    val recipient = messages.firstOrNull()?.address.orEmpty()

    // Load E2E state for this recipient
    LaunchedEffect(recipient) {
        if (recipient.isNotBlank()) {
            val registry = PeerRegistry(ctx)
            isOptedIn   = registry.isEncryptionOptedIn(recipient)
            isEncrypted = registry.getHandshakeState(recipient) == PeerRegistry.HandshakeState.ESTABLISHED
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(recipient.ifBlank { "Conversation" }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    // Encryption badge in toolbar
                    if (recipient.isNotBlank()) {
                        EncryptionBadge(
                            isEncrypted  = isEncrypted,
                            isOptedIn    = isOptedIn,
                            onToggle     = {
                                scope.launch {
                                    val registry = PeerRegistry(ctx)
                                    val newState = !isOptedIn
                                    registry.setEncryptionOptIn(recipient, newState)
                                    isOptedIn = newState
                                    if (newState && !isEncrypted) {
                                        // Initiate handshake
                                        SmsSender.sendHandshake(ctx, recipient)
                                    }
                                }
                            },
                            onViewSafety = { onSafetyNumber(recipient) },
                            modifier     = Modifier.padding(end = 8.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            Column {
                // Reply preview bar
                replyingTo?.let { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.Reply, null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            reply.body.take(60) + if (reply.body.length > 60) "" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { replyingTo = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.Close, "Cancel reply",
                                modifier = Modifier.size(16.dp))
                        }
                    }
                }
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
                                replyingTo = null
                                reload()
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center) {
                        Text("No messages yet", textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    SwipeableMessageBubble(
                        msg = msg,
                        onSwipeReply = { replyingTo = msg }
                    )
                }
            }
        }
    }
}

//  Swipeable bubble 

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SwipeableMessageBubble(
    msg: Message,
    onSwipeReply: () -> Unit
) {
    val ctx = LocalContext.current
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var menuOpen by remember { mutableStateOf(false) }

    val incoming = msg.isIncoming
    val bg = if (incoming) MaterialTheme.colorScheme.surfaceVariant
             else MaterialTheme.colorScheme.primary
    val fg = if (incoming) MaterialTheme.colorScheme.onSurfaceVariant
             else MaterialTheme.colorScheme.onPrimary

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (incoming) Arrangement.Start else Arrangement.End
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX.value > 80f) onSwipeReply()
                            scope.launch { offsetX.animateTo(0f, tween(200)) }
                        }
                    ) { _, dragAmount ->
                        scope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount).coerceIn(0f, 120f))
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true }
                    )
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                if (msg.attachments.isNotEmpty()) {
                    msg.attachments.forEach { att -> AttachmentView(att, fg) }
                }
                if (msg.body.isNotEmpty()) {
                    Text(msg.body, color = fg)
                }
                // Timestamp + lock icon row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(msg.date)),
                        style = MaterialTheme.typography.labelSmall,
                        color = fg.copy(alpha = 0.7f)
                    )
                    if (msg.isEncrypted) {
                        MessageEncryptedIcon()
                    }
                }

                // Context menu
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, onClick = {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Fixd", msg.body))
                        Toast.makeText(ctx, "Copied", Toast.LENGTH_SHORT).show()
                        menuOpen = false
                    })
                    DropdownMenuItem(text = { Text("Reply") }, onClick = {
                        onSwipeReply(); menuOpen = false
                    })
                    DropdownMenuItem(text = { Text("Forward") }, onClick = {
                        val i = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, msg.body)
                        }
                        ctx.startActivity(Intent.createChooser(i, "Forward"))
                        menuOpen = false
                    })
                }
            }

            // Swipe hint icon
            if (offsetX.value > 40f) {
                Icon(
                    Icons.Filled.Reply, "Reply",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 4.dp)
                        .size(20.dp)
                )
            }
        }
    }
}

//  Attachment renderer (unchanged from original) 

@Composable
private fun AttachmentView(att: Attachment, fg: androidx.compose.ui.graphics.Color) {
    val ctx = LocalContext.current
    val isImage = att.contentType.startsWith("image/")
    val isVideo = att.contentType.startsWith("video/")
    when {
        isImage -> {
            val bmp = remember(att.partId) {
                runCatching {
                    ctx.contentResolver.openInputStream(
                        android.net.Uri.parse("content://mms/part/${att.partId}")
                    )?.use { android.graphics.BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }.getOrNull()
            }
            if (bmp != null) {
                Image(bitmap = bmp, contentDescription = att.filename,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                    contentScale = ContentScale.Fit)
            } else {
                Text("[image]", color = fg)
            }
        }
        isVideo -> {
            val bmp = remember(att.partId) {
                runCatching {
                    val tmp = java.io.File(ctx.cacheDir, "vthumb_${att.partId}.bin")
                    if (!tmp.exists()) {
                        ctx.contentResolver.openInputStream(
                            android.net.Uri.parse("content://mms/part/${att.partId}")
                        )?.use { i -> tmp.outputStream().use { o -> i.copyTo(o) } }
                    }
                    android.media.MediaMetadataRetriever().run {
                        try { setDataSource(tmp.absolutePath); getFrameAtTime(0)?.asImageBitmap() }
                        finally { runCatching { release() } }
                    }
                }.getOrNull()
            }
            Box(
                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable {
                    val uri = android.net.Uri.parse("content://mms/part/${att.partId}")
                    val i = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, att.contentType)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { ctx.startActivity(Intent.createChooser(i, "Open video")) }
                }
            ) {
                if (bmp != null) {
                    Image(bitmap = bmp, contentDescription = att.filename,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                        contentScale = ContentScale.Fit)
                    Icon(Icons.Filled.PlayArrow, "Play",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.align(Alignment.Center).size(48.dp))
                } else Text("[video]", color = fg)
            }
        }
        !att.text.isNullOrEmpty() -> Text(att.text!!, color = fg)
        else -> Text("[${att.filename ?: att.contentType}]", color = fg)
    }
}
