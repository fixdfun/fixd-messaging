package app.fixd.messaging.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Small composable shown in the conversation toolbar / message bubble.
 *
 * [isEncrypted]    true when the session is established
 * [isOptedIn]      true when the user toggled encryption on
 * [onToggle]       called when the user taps the badge (to toggle opt-in)
 * [onViewSafety]   called when the user long-presses (to view safety number)
 */
@Composable
fun EncryptionBadge(
    isEncrypted: Boolean,
    isOptedIn: Boolean,
    onToggle: () -> Unit,
    onViewSafety: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = when {
        isEncrypted && isOptedIn -> Color(0xFF2E7D32)   // green  active E2E
        isOptedIn                -> Color(0xFFE65100)   // orange  opted in, handshake pending
        else                     -> Color(0xFF616161)   // grey  plain SMS
    }
    val icon = if (isEncrypted && isOptedIn) Icons.Filled.Lock else Icons.Filled.LockOpen
    val label = when {
        isEncrypted && isOptedIn -> "E2E"
        isOptedIn                -> "Pending"
        else                     -> "SMS"
    }

    Row(
        modifier = modifier
            .background(bgColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/**
 * Per-message lock icon shown in message bubbles for E2E encrypted messages.
 * Shown as a tiny padlock at the trailing edge of the message timestamp row.
 */
@Composable
fun MessageEncryptedIcon(modifier: Modifier = Modifier) {
    Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = "Encrypted",
        tint = Color(0xFF2E7D32),
        modifier = modifier.size(12.dp)
    )
}
