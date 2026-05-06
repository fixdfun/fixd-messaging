package app.fixd.messaging.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fixd.messaging.backup.BackupManager
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Text("Backup & transfer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    val n = BackupManager(ctx).exportToDownloads()
                    status = "Exported $n messages to Downloads/fixd-messaging-backup.json"
                }
            }) { Text("Export SMS to Downloads") }
            Spacer(Modifier.height(16.dp))
            Text("Keyboard", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                ctx.startActivity(intent)
            }) { Text("Enable Fixd keyboard") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = {
                val intent = Intent(Settings.ACTION_USER_DICTIONARY_SETTINGS)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                runCatching { ctx.startActivity(intent) }
            }) { Text("User dictionary") }
            status?.let {
                Spacer(Modifier.height(16.dp))
                Text(it)
            }
            Spacer(Modifier.weight(1f))
            Text("Fixd Messaging • fixd.fun", style = MaterialTheme.typography.labelSmall)
        }
    }
}
