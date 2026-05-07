package app.fixd.messaging.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

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
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
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
                Text("Fixd Messaging", modifier = Modifier.padding(16.dp), fontWeight = FontWeight.Bold)
                HorizontalDivider()
                NavigationDrawerItem(
                    label = { Text("Conversations") },
                    selected = true,
                    onClick = { drawerScope.launch { drawerState.close() } },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                NavigationDrawerItem(
                    label = { Text("Settings") },
                    selected = false,
                    onClick = { drawerScope.launch { drawerState.close() }; onSettings() },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                HorizontalDivider()
                Text("Cloud backup is in Settings", modifier = Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
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
