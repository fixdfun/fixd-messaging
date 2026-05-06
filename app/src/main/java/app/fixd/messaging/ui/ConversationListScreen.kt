package app.fixd.messaging.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fixd.messaging.data.Conversation
import app.fixd.messaging.data.ConversationsRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    isDefaultSmsApp: Boolean,
    onRequestDefault: () -> Unit
) {
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(isDefaultSmsApp) {
        conversations = if (isDefaultSmsApp) ConversationsRepository.load(ctx) else emptyList()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fixd Messaging", fontWeight = FontWeight.SemiBold) }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { /* navigate to compose */ }) {
                Icon(Icons.Filled.Edit, contentDescription = "New message")
            }
        }
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            if (!isDefaultSmsApp) {
                Card(Modifier.padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Set Fixd as your default SMS app", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text("Required so Fixd can send and receive your text messages.")
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onRequestDefault) { Text("Make Fixd Default") }
                    }
                }
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(conversations, key = { it.threadId }) { c ->
                    ListItem(
                        headlineContent = { Text(c.address, fontWeight = FontWeight.SemiBold) },
                        supportingContent = { Text(c.snippet, maxLines = 1) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}
