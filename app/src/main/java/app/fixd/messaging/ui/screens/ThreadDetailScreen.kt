package app.fixd.messaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
            Text(msg.body, color = fg)
            Text(
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(msg.date)),
                style = MaterialTheme.typography.labelSmall,
                color = fg.copy(alpha = 0.7f)
            )
        }
    }
}
