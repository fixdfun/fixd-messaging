package app.fixd.messaging.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.fixd.messaging.crypto.PeerRegistry
import kotlinx.coroutines.launch

/**
 * Safety Number verification screen.
 *
 * Shows the identity key fingerprint for [address] so users can verify
 * out-of-band (e.g. read aloud or compare QR) that the key hasn't changed.
 * Modeled after Signal's safety-number flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SafetyNumberScreen(
    address: String,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var fingerprint by remember { mutableStateOf<String?>(null) }
    var handshakeState by remember { mutableStateOf<PeerRegistry.HandshakeState>(PeerRegistry.HandshakeState.NONE) }

    LaunchedEffect(address) {
        val registry = PeerRegistry(ctx)
        fingerprint = registry.getFingerprint(address)
        handshakeState = registry.getHandshakeState(address)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Safety Number") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Status badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = if (handshakeState == PeerRegistry.HandshakeState.ESTABLISHED)
                        Color(0xFF2E7D32) else Color(0xFF616161)
                )
                Text(
                    text = when (handshakeState) {
                        PeerRegistry.HandshakeState.ESTABLISHED -> "Session established with $address"
                        PeerRegistry.HandshakeState.SENT        -> "Handshake sent  awaiting response"
                        PeerRegistry.HandshakeState.NONE        -> "No secure session"
                    },
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Divider()

            // Fingerprint display
            if (fingerprint != null) {
                Text(
                    text = "Safety Number",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Compare this number with $address using another channel " +
                           "(call, in person) to verify the session hasn't been tampered with.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Display fingerprint in 5-char groups for readability
                val grouped = fingerprint!!.chunked(5).joinToString(" ")
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(12.dp)
                        )
                        .padding(16.dp)
                ) {
                    Text(
                        text = grouped,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                        lineHeight = 22.sp
                    )
                }
            } else {
                Text(
                    text = "No safety number yet  start a secure session first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Reset session button
            if (handshakeState != PeerRegistry.HandshakeState.NONE) {
                var showConfirm by remember { mutableStateOf(false) }
                OutlinedButton(
                    onClick = { showConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset Secure Session")
                }
                if (showConfirm) {
                    AlertDialog(
                        onDismissRequest = { showConfirm = false },
                        title = { Text("Reset session?") },
                        text = {
                            Text(
                                "This will delete the current secure session with $address. " +
                                "You will need to perform a new handshake to re-establish E2E encryption."
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                scope.launch {
                                    PeerRegistry(ctx).resetPeer(address)
                                    handshakeState = PeerRegistry.HandshakeState.NONE
                                    fingerprint = null
                                    showConfirm = false
                                }
                            }) { Text("Reset", color = MaterialTheme.colorScheme.error) }
                        },
                        dismissButton = {
                            TextButton(onClick = { showConfirm = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}
