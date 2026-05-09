package app.fixd.messaging.sms

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import app.fixd.messaging.FixdApp
import app.fixd.messaging.MainActivity
import app.fixd.messaging.R
import app.fixd.messaging.crypto.Envelope
import app.fixd.messaging.crypto.FixdSignal
import app.fixd.messaging.crypto.PeerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val TAG = "SmsReceiver"

/**
 * Receives incoming SMS broadcasts (SMS_DELIVER_ACTION) when we are the default app.
 *
 * Fixd envelope handling:
 *   FXD1:H:*   PreKeyBundle handshake  process and reply with our own bundle if we
 *               haven't already. Swallow (don't persist to inbox).
 *   FXD1:M:*   Encrypted message segment  buffer chunks, decrypt when complete,
 *               persist PLAINTEXT to inbox with encrypted=1 flag in subject field.
 *   plaintext  Persist as-is (existing behaviour).
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (msgs.isEmpty()) return

        val from = msgs[0].originatingAddress.orEmpty()
        val body = msgs.joinToString(separator = "") { it.messageBody.orEmpty() }
        val ts   = msgs[0].timestampMillis

        // Offload all Fixd processing to IO coroutine (DataStore is suspend-only)
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            handleIncoming(ctx, from, body, ts)
        }
    }

    private suspend fun handleIncoming(ctx: Context, from: String, body: String, ts: Long) {
        when (val parsed = Envelope.parse(body)) {
            is Envelope.ParseResult.NotFixd -> {
                // Plain SMS  standard path
                persistToInbox(ctx, from, body, ts, encrypted = false)
                postNotification(ctx, from, body)
            }

            is Envelope.ParseResult.Complete -> {
                when (parsed.type) {
                    Envelope.TYPE_HANDSHAKE -> handleHandshake(ctx, from, parsed.payload)
                    Envelope.TYPE_MESSAGE   -> handleEncryptedMessage(ctx, from, parsed.payload, ts)
                    Envelope.TYPE_REKEY     -> {
                        // Reset session and process new bundle
                        val registry = PeerRegistry(ctx)
                        registry.resetPeer(from)
                        handleHandshake(ctx, from, parsed.payload)
                    }
                    else -> Log.w(TAG, "Unknown Fixd type '${parsed.type}' from $from")
                }
            }

            is Envelope.ParseResult.Chunk -> {
                val registry = PeerRegistry(ctx)
                registry.storeChunk(from, parsed.msgId, parsed.seq, parsed.total, parsed.b64Chunk)
                val allChunks = registry.collectChunks(from, parsed.msgId)
                val payload = Envelope.reassemble(allChunks, parsed.total)
                if (payload != null) {
                    registry.clearChunks(from, parsed.msgId)
                    when (parsed.type) {
                        Envelope.TYPE_HANDSHAKE -> handleHandshake(ctx, from, payload)
                        Envelope.TYPE_MESSAGE   -> handleEncryptedMessage(ctx, from, payload, ts)
                        else -> Log.w(TAG, "Unknown chunked Fixd type '${parsed.type}' from $from")
                    }
                }
                // else: still waiting for more chunks  nothing to do yet
            }

            is Envelope.ParseResult.Malformed -> {
                Log.w(TAG, "Malformed Fixd envelope from $from: ${parsed.reason}")
                // Treat as plaintext so the user sees something
                persistToInbox(ctx, from, body, ts, encrypted = false)
                postNotification(ctx, from, body)
            }
        }
    }

    //  Handshake handling 

    private suspend fun handleHandshake(ctx: Context, from: String, bundleBytes: ByteArray) {
        val signal = FixdSignal(ctx)
        val registry = PeerRegistry(ctx)
        runCatching {
            signal.processIncomingHandshake(from, bundleBytes)
            Log.i(TAG, "Handshake complete with $from")

            // If we haven't yet sent OUR bundle to them, reply now
            val state = registry.getHandshakeState(from)
            if (state == PeerRegistry.HandshakeState.ESTABLISHED) {
                // processIncomingHandshake sets ESTABLISHED; we only reply if we
                // haven't already sent a handshake (i.e. they initiated)
                val ourSegments = signal.initiateHandshake(from)
                SmsSender.sendHandshake(ctx, from) // reply with our PreKeyBundle
                Log.i(TAG, "Replied with our PreKeyBundle to $from")
            }
        }.onFailure {
            Log.e(TAG, "Handshake processing failed from $from: ${it.message}")
        }
        // Handshake messages are never persisted to inbox
    }

    //  Encrypted message handling 

    private suspend fun handleEncryptedMessage(
        ctx: Context, from: String, cipherBytes: ByteArray, ts: Long
    ) {
        val signal = FixdSignal(ctx)
        val plaintext = signal.decryptMessage(from, cipherBytes)
        if (plaintext != null) {
            persistToInbox(ctx, from, plaintext, ts, encrypted = true)
            postNotification(ctx, from, plaintext)
        } else {
            Log.e(TAG, "Could not decrypt message from $from  session may be stale")
            // Optionally: show " Unable to decrypt message" placeholder
            val placeholder = " [Encrypted message  could not decrypt. Tap to reset session.]"
            persistToInbox(ctx, from, placeholder, ts, encrypted = true)
            postNotification(ctx, from, placeholder)
        }
    }

    //  Persistence 

    private fun persistToInbox(
        ctx: Context, from: String, body: String, ts: Long, encrypted: Boolean
    ) {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, from)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, ts)
            put(Telephony.Sms.DATE_SENT, ts)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
            // Repurpose the SUBJECT column as a lightweight flag for the UI lock icon
            if (encrypted) put(Telephony.Sms.SUBJECT, "fixd:encrypted")
        }
        runCatching { ctx.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values) }
            .onFailure { Log.e(TAG, "Failed to insert inbox message: ${it.message}") }
    }

    //  Notification 

    private fun postNotification(ctx: Context, from: String, body: String) {
        val tapIntent = Intent(ctx, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(
            ctx, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val displayBody = if (body.startsWith("")) body else body
        val notif = NotificationCompat.Builder(ctx, FixdApp.CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(from.ifBlank { "New message" })
            .setContentText(displayBody)
            .setStyle(NotificationCompat.BigTextStyle().bigText(displayBody))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(from.hashCode(), notif) }
    }
}
