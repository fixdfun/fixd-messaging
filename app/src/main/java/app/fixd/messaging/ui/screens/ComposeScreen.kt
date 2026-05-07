package app.fixd.messaging.ui.screens

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.material.icons.filled.PlayArrow
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.fixd.messaging.mms.MmsPart
import app.fixd.messaging.mms.MmsSender
import app.fixd.messaging.sms.SmsSender
import app.fixd.messaging.prefs.FixdPrefs
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                val recipients = to.split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
                val sig = FixdPrefs(ctx).signature
            val b = if (sig.isNotBlank() && !body.endsWith(sig)) (if (body.isBlank()) sig else body.trimEnd() + "\n\n" + sig) else body
                if (recipients.isEmpty() || (b.isBlank() && attachments.isEmpty())) {
                    status = "Enter recipient and message"
                    return@FloatingActionButton
                }
                scope.launch {
                    val ok = if (attachments.isEmpty() && recipients.size == 1) {
                        SmsSender.send(ctx, recipients[0], b) >= 0
                    } else {
                        val parts = buildList {
                            if (b.isNotBlank()) add(MmsPart("text/plain", b.toByteArray(Charsets.UTF_8), "text.txt"))
                            attachments.forEach { add(MmsPart(it.mime, it.bytes, it.name)) }
                        }
                        MmsSender.send(ctx, recipients, null, parts)
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
                label = { Text("To (comma-separate for group MMS)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text("Message") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            )
            if (attachments.isNotEmpty()) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(attachments) { att ->
                        val idx = attachments.indexOf(att)
                        AttachmentThumbnail(att) {
                            attachments = attachments.toMutableList().also { it.removeAt(idx) }
                        }
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

@Composable
private fun AttachmentThumbnail(att: PickedAttachment, onRemove: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val bmp = remember(att.uri, att.mime) {
        runCatching {
            when {
                att.mime.startsWith("image/") ->
                    BitmapFactory.decodeByteArray(att.bytes, 0, att.bytes.size)?.asImageBitmap()
                att.mime.startsWith("video/") -> {
                    val tmp = java.io.File(ctx.cacheDir, "thumb_${att.uri.hashCode()}.bin").apply {
                        if (!exists()) writeBytes(att.bytes)
                    }
                    val mmr = android.media.MediaMetadataRetriever()
                    try {
                        mmr.setDataSource(tmp.absolutePath)
                        mmr.getFrameAtTime(0)?.asImageBitmap()
                    } finally {
                        runCatching { mmr.release() }
                    }
                }
                else -> null
            }
        }.getOrNull()
    }
    Box(
        Modifier
            .size(80.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp,
                contentDescription = att.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
            if (att.mime.startsWith("video/")) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "video",
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
            }
        } else {
            Text(
                att.name.take(12),
                modifier = Modifier.align(Alignment.Center).padding(4.dp),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = "remove")
        }
    }
}
