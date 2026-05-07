package app.fixd.messaging.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fixd.messaging.mms.MmsPart
import app.fixd.messaging.mms.MmsSender
import app.fixd.messaging.sms.SmsSender
import kotlinx.coroutines.launch

private data class PickedAttachment(val uri: Uri, val mime: String, val name: String, val bytes: ByteArray)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var to by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var attachments by remember { mutableStateOf(listOf<PickedAttachment>()) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            val mime = ctx.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
            attachments = attachments + PickedAttachment(uri, mime, name, bytes)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val t = to.trim()
                val b = body
                if (t.isBlank() || (b.isBlank() && attachments.isEmpty())) {
                    status = "Recipient and message or attachment required"
                    return@FloatingActionButton
                }
                scope.launch {
                    val ok = if (attachments.isEmpty()) {
                        SmsSender.send(ctx, t, b) >= 0
                    } else {
                        val parts = buildList {
                            if (b.isNotBlank()) add(MmsPart("text/plain", b.toByteArray(Charsets.UTF_8), "text.txt"))
                            attachments.forEach { add(MmsPart(it.mime, it.bytes, it.name)) }
                        }
                        MmsSender.send(ctx, t, null, parts)
                    }
                    status = if (ok) "Sent" else "Send failed"
                    if (ok) onBack()
                }
            }) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = MaterialTheme.colorScheme.onPrimary) }
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = to,
                onValueChange = { to = it },
                label = { Text("To (phone number)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                maxLines = 20,
            )
            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    attachments.forEachIndexed { idx, att ->
                        AssistChip(
                            onClick = {
                                attachments = attachments.toMutableList().also { it.removeAt(idx) }
                            },
                            label = { Text("${'$'}{att.name}  ✕") },
                        )
                    }
                }
            }
            OutlinedButton(
                onClick = { picker.launch(arrayOf("image/*", "video/*")) },
                shape = RoundedCornerShape(50),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(if (attachments.isEmpty()) "Attach picture" else "Add another")
            }
            status?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
