package `fun`.fixd.messaging.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import `fun`.fixd.messaging.mms.MmsSender
import `fun`.fixd.messaging.sms.SmsSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComposeScreen(onSent: () -> Unit) {
    val context = LocalContext.current
    var to by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf(listOf<MmsSender.Attachment>()) }
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "attachment"
            attachments = attachments + MmsSender.Attachment(
                mime = mime,
                fileName = name,
                localPath = uri.toString(),
                bytes = bytes,
            )
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("New message") }) }) { padding ->
        Column(
            Modifier.padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            if (attachments.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    attachments.forEachIndexed { idx, att ->
                        AssistChip(
                            onClick = {
                                attachments = attachments.toMutableList().also { it.removeAt(idx) }
                            },
                            label = { Text("${'$'}{att.fileName}  ") },
                            shape = RoundedCornerShape(50),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(arrayOf("image/*", "video/*")) }) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Attach")
                }
                Button(onClick = {
                    val ok = if (attachments.isEmpty()) {
                        SmsSender(context).send(to, body)
                    } else {
                        MmsSender(context).send(to, body.takeIf { it.isNotBlank() }, attachments)
                    }
                    status = if (ok) "Sent" else "Failed"
                    if (ok) onSent()
                }) {
                    Icon(Icons.Default.Send, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (attachments.isEmpty()) "Send SMS" else "Send MMS")
                }
            }
            status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
