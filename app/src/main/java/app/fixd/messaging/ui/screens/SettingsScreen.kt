package app.fixd.messaging.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fixd.messaging.backup.BackupManager
import app.fixd.messaging.prefs.FixdPrefs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { FixdPrefs(ctx) }
    var status by remember { mutableStateOf<String?>(null) }
    var signature by remember { mutableStateOf(prefs.signature) }
    var emojiCat by remember { mutableStateOf(prefs.defaultEmojiCategory) }
    var enterSends by remember { mutableStateOf(prefs.enterKeySends) }
    var emojiMenuOpen by remember { mutableStateOf(false) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val n = runCatching {
                    ctx.contentResolver.openInputStream(uri)?.use { BackupManager(ctx).importFromStream(it) } ?: 0
                }.getOrDefault(0)
                status = "Imported $n records"
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp).verticalScroll(rememberScrollState())
        ) {
            Text("Backup & transfer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                scope.launch {
                    val n = BackupManager(ctx).exportToDownloads()
                    status = "Exported $n messages to Downloads/fixd-messaging-backup.json"
                }
            }) { Text("Export SMS to Downloads") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "*/*")) }) {
                Text("Import backup")
            }

            Spacer(Modifier.height(24.dp))
            Text("Composer", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = signature,
                onValueChange = { signature = it; prefs.signature = it },
                label = { Text("Signature (appended to outgoing messages)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Switch(checked = enterSends, onCheckedChange = { enterSends = it; prefs.enterKeySends = it })
                Spacer(Modifier.width(8.dp))
                Text("Enter key sends message")
            }

            Spacer(Modifier.height(24.dp))
            Text("Keyboard", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            ExposedDropdownMenuBox(
                expanded = emojiMenuOpen,
                onExpandedChange = { emojiMenuOpen = it }
            ) {
                OutlinedTextField(
                    value = FixdPrefs.EMOJI_CATEGORIES.getOrNull(emojiCat) ?: "Smileys",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Default emoji category") },
                    trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) },
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                )
                ExposedDropdownMenu(
                    expanded = emojiMenuOpen,
                    onDismissRequest = { emojiMenuOpen = false }
                ) {
                    FixdPrefs.EMOJI_CATEGORIES.forEachIndexed { i, name ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                emojiCat = i
                                prefs.defaultEmojiCategory = i
                                emojiMenuOpen = false
                            }
                        )
                    }
                }
            }
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
            Spacer(Modifier.height(32.dp))
            Text("Fixd Messaging  fixd.fun", style = MaterialTheme.typography.labelSmall)
        }
    }
}
