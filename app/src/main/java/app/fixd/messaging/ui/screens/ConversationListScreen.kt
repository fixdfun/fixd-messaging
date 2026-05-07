package app.fixd.messaging.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.fixd.messaging.data.Conversation
import app.fixd.messaging.data.MessageRepository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(
    isDefaultSmsApp: Boolean,
    onRequestDefault: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenThread: (Long) -> Unit,
    onCompose: () -> Unit,
    onSettings: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember { MessageRepository(ctx) }
    var conversations by remember { mutableStateOf<List<Conversation>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val drawerScope = rememberCoroutineScope()

    LaunchedEffect(isDefaultSmsApp) {
        if (isDefaultSmsApp) {
            scope.launch {
                conversations = repo.loadConversations()
                loaded = true
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(16.dp))
                Text("  Fixd Messaging", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
                Text("  ${conversations.size} conversation${if (conversations.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 16.dp, bottom = 16.dp))
                NavigationDrawerItem(label = { Text("Conversations") }, icon = { Icon(Icons.Default.MailOutline, null) }, selected = true, onClick = { drawerScope.launch { drawerState.close() } })
                NavigationDrawerItem(label = { Text("Cloud Backup") }, icon = { Icon(Icons.Default.CloudUpload, null) }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onSettings() })
                NavigationDrawerItem(label = { Text("Settings") }, icon = { Icon(Icons.Default.Settings, null) }, selected = false, onClick = { drawerScope.launch { drawerState.close() }; onSettings() })
                NavigationDrawerItem(label = { Text("About") }, icon = { Icon(Icons.Default.Info, null) }, selected = false, onClick = { drawerScope.launch { drawerState.close() } })
            }
        }
    ) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fixd Messaging", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { drawerScope.launch { drawerState.open() } }) {
                        Icon(Icons.Default.Menu, "Menu", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, "Settings", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            if (isDefaultSmsApp) {
                ExtendedFloatingActionButton(onClick = onCompose, icon = { Icon(Icons.Default.Add, null) }, text = { Text("New") })
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (!isDefaultSmsApp) {
                NotDefaultBanner(onRequestDefault, onRequestPermissions)
            } else if (!loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else if (conversations.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No conversations yet. Tap + to start one.", style = MaterialTheme.typography.bodyLarge)
                }
            } else {
                LazyColumn {
                    items(conversations, key = { it.threadId }) { conv ->
                        ConversationRow(conv) { onOpenThread(conv.threadId) }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: Conversation, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = {
            Text(
                conv.displayName ?: conv.address.ifBlank { "(unknown)" },
                fontWeight = if (conv.unreadCount > 0) FontWeight.Bold else FontWeight.Normal
            )
        },
        supportingContent = { Text(conv.snippet, maxLines = 1) },
        trailingContent = {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(conv.date)),
                style = MaterialTheme.typography.labelSmall
            )
        }
    )
}

@Composable
private fun NotDefaultBanner(onRequestDefault: () -> Unit, onRequestPermissions: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Welcome to Fixd Messaging", style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(8.dp))
            Text("To send and receive SMS/MMS, set Fixd as your default messaging app.",
                color = MaterialTheme.colorScheme.onPrimaryContainer)
            Spacer(Modifier.height(12.dp))
            Row {
                Button(onClick = onRequestDefault) { Text("Set as default") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = onRequestPermissions) { Text("Permissions") }
            }
        }
    }
    }

}
