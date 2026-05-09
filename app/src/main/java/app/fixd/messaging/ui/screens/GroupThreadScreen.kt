package app.fixd.messaging.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.fixd.messaging.data.GroupRepository
import app.fixd.messaging.data.Message
import app.fixd.messaging.data.MessageRepository
import app.fixd.messaging.mms.GroupMessageDispatcher
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

/**
 * Full-screen group thread view.
 *
 * Features:
 *  - Group name + member count in the top bar
 *  - Sender label above each incoming bubble (shows who sent in the group)
 *  - Swipe-right on any bubble to quote-reply (sets [replyingTo])
 *  - Reply preview bar above the composer
 *  - Message dispatch via [GroupMessageDispatcher]
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupThreadScreen(groupId: String, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val groupRepo = remember { GroupRepository(ctx) }
    val msgRepo   = remember { MessageRepository(ctx) }

    var group    by remember { mutableStateOf<app.fixd.messaging.data.GroupConversation?>(null) }
    var messages by remember { mutableStateOf<List<Message>>(emptyList()) }
    var input    by remember { mutableStateOf("") }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()

    suspend fun reload() {
        group = groupRepo.getGroup(groupId)
        val tid = group?.threadId ?: return
        if (tid != -1L) {
            messages = msgRepo.loadMessages(tid)
            if (messages.isNotEmpty()) listState.scrollToItem(messages.lastIndex)
            msgRepo.markThreadRead(tid)
        }
    }

    LaunchedEffect(groupId) { reload() }

    val groupName   = group?.name ?: "Group"
    val memberCount = group?.members?.size ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(groupName)
                        Text(
                            "$memberCount members",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    Icon(Icons.Filled.Group, contentDescription = "Group",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(end = 12.dp))
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
                // Reply preview
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
                        IconButton(onClick = { replyingTo = null }, modifier = Modifier.size(32.dp)) {
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
                        placeholder = { Text("Message ${groupName}") },
                        modifier = Modifier.weight(1f),
                        maxLines = 5
                    )
                    Spacer(Modifier.width(4.dp))
                    FilledIconButton(onClick = {
                        val text = input.trim()
                        if (text.isBlank()) return@FilledIconButton
                        val g = group ?: return@FilledIconButton
                        scope.launch {
                            GroupMessageDispatcher.send(ctx, g, text)
                            input = ""
                            replyingTo = null
                            reload()
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
                        Text("No messages yet  say something!",
                            textAlign = TextAlign.Center)
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    GroupMessageBubble(
                        msg = msg,
                        onSwipeReply = { replyingTo = msg }
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupMessageBubble(
    msg: Message,
    onSwipeReply: () -> Unit
) {
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
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
                            if (offsetX.value > 80f) {
                                onSwipeReply()
                            }
                            scope.launch {
                                offsetX.animateTo(0f, tween(200))
                            }
                        }
                    ) { _, dragAmount ->
                        scope.launch {
                            val newVal = (offsetX.value + dragAmount).coerceIn(0f, 120f)
                            offsetX.snapTo(newVal)
                        }
                    }
                }
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // Sender label for incoming group messages
                if (incoming && msg.address.isNotBlank()) {
                    Text(
                        msg.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontStyle = FontStyle.Italic
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (msg.body.isNotEmpty()) {
                    Text(msg.body, color = fg)
                }
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
                        app.fixd.messaging.ui.MessageEncryptedIcon()
                    }
                    if (msg.isMms) {
                        Text("MMS", style = MaterialTheme.typography.labelSmall,
                            color = fg.copy(alpha = 0.5f))
                    }
                }
            }
            // Swipe hint icon
            if (offsetX.value > 40f) {
                Icon(
                    Icons.Filled.Reply,
                    contentDescription = "Reply",
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
