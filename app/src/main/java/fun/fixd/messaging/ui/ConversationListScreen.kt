package `fun`.fixd.messaging.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `fun`.fixd.messaging.data.ContactResolver
import `fun`.fixd.messaging.data.MessageRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    onOpenThread: (String) -> Unit,
    onCompose: () -> Unit,
    onSettings: () -> Unit,
) {
    val context = LocalContext.current
    val resolver = remember { ContactResolver(context) }
    val conversations by produceState(initialValue = emptyList<`fun`.fixd.messaging.data.Conversation>()) {
        value = MessageRepository(context).loadConversations()
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fixd Messaging") },
                actions = {
                    TextButton(onClick = onSettings) { Text("Settings") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCompose) {
                Icon(Icons.Default.Add, contentDescription = "New message")
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize()) {
            items(conversations) { c ->
                ListItem(
                    headlineContent = { Text(resolver.displayName(c.address)) },
                    supportingContent = { Text(c.snippet) },
                    modifier = Modifier.clickable { onOpenThread(c.threadId) },
                )
                HorizontalDivider()
            }
        }
    }
}
