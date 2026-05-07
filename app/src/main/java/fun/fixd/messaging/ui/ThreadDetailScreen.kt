package `fun`.fixd.messaging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `fun`.fixd.messaging.data.ContactResolver
import `fun`.fixd.messaging.data.Message
import `fun`.fixd.messaging.data.MessageRepository
import `fun`.fixd.messaging.sms.SmsSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadDetailScreen(threadId: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val resolver = remember { ContactResolver(context) }
    var messages by remember { mutableStateOf(emptyList<Message>()) }
    var draft by remember { mutableStateOf("") }
    val title = remember(messages) {
        messages.firstOrNull()?.address?.let(resolver::displayName) ?: "Conversation"
    }
    LaunchedEffect(threadId) {
        messages = MessageRepository(context).loadMessages(threadId)
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).padding(8.dp), reverseLayout = true) {
                items(messages.reversed()) { msg ->
                    val isMe = msg.outgoing
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
                    ) {
                        Surface(
                            color = if (isMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.widthIn(max = 280.dp),
                        ) {
                            Text(
                                msg.body,
                                color = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = {
                    val to = messages.firstOrNull()?.address ?: return@IconButton
                    if (SmsSender(context).send(to, draft)) {
                        draft = ""
                        messages = MessageRepository(context).loadMessages(threadId)
                    }
                }) { Icon(Icons.Default.Send, contentDescription = "Send") }
            }
        }
    }
}
