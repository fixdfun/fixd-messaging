package app.fixd.messaging.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.fixd.messaging.mms.MmsSender
import app.fixd.messaging.sms.SmsSender
import app.fixd.messaging.theme.FixdTheme

class ComposeMessageActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FixdTheme {
                Surface(Modifier.fillMaxSize()) { ComposeScreen() }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ComposeScreen() {
    var to by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var attachments by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> if (uris.isNotEmpty()) attachments = attachments + uris }

    Scaffold(topBar = { TopAppBar(title = { Text("New message") }) }) { padding ->
        Column(Modifier.padding(padding).padding(16.dp).fillMaxSize()) {
            OutlinedTextField(value = to, onValueChange = { to = it }, label = { Text("To") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(value = body, onValueChange = { body = it }, label = { Text("Message") }, modifier = Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(12.dp))
            if (attachments.isNotEmpty()) Text("${attachments.size} attachment(s) ready")
            Row {
                OutlinedButton(onClick = { pickImage.launch(androidx.activity.result.PickVisualMediaRequest()) }) {
                    Icon(Icons.Filled.Image, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Add picture")
                }
                Spacer(Modifier.width(12.dp))
                Button(onClick = {
                    if (attachments.isEmpty()) SmsSender.send(ctx, to, body)
                    else MmsSender.send(ctx, to, body, attachments)
                }) {
                    Icon(Icons.Filled.Send, contentDescription = null); Spacer(Modifier.width(6.dp)); Text("Send")
                }
            }
        }
    }
}
