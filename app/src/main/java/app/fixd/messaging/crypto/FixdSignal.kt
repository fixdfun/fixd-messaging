package app.fixd.messaging.crypto

import android.content.Context
import android.util.Log
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.security.SecureRandom

private const val TAG = "FixdSignal"
private const val DEVICE_ID = 1

/**
 * High-level facade for all Signal Protocol operations in Fixd Messaging.
 *
 * Lifecycle:
 *   1. Call [init] once at app startup (FixdApp.onCreate).
 *   2. Before sending a message, check [shouldEncrypt].
 *   3. If yes, call [encryptMessage]  returns FXD1 envelope strings (one per SMS segment).
 *   4. On receive in SmsReceiver, call [handleIncoming]  returns plaintext or null if it
 *      was a handshake/control envelope that was consumed.
 *   5. To start a secure session with a contact, call [initiateHandshake]  returns the
 *      FXD1:H: strings to send as SMS.
 */
class FixdSignal(private val ctx: Context) {

    private val identityStore  = FixdIdentityKeyStore(ctx)
    private val preKeyStore    = FixdPreKeyStore(ctx)
    private val signedPKStore  = FixdSignedPreKeyStore(ctx)
    private val sessionStore   = FixdSessionStore(ctx)
    private val peers          = PeerRegistry(ctx)

    //  Initialisation 

    /**
     * Ensure we have a valid signed prekey and a batch of one-time prekeys
     * generated and stored. Safe to call multiple times.
     */
    fun init() {
        ensureSignedPreKey()
        ensureOneTimePreKeys()
    }

    private fun ensureSignedPreKey() {
        val existing = signedPKStore.loadSignedPreKeys()
        if (existing.isNotEmpty()) return
        val idPair = identityStore.getIdentityKeyPair()
        val kp = Curve.generateKeyPair()
        val sig = Curve.calculateSignature(
            idPair.privateKey,
            kp.publicKey.serialize()
        )
        val record = SignedPreKeyRecord(1, System.currentTimeMillis(), kp, sig)
        signedPKStore.storeSignedPreKey(1, record)
        Log.i(TAG, "Generated initial signed prekey")
    }

    private fun ensureOneTimePreKeys() {
        // Generate 100 one-time prekeys if we have none
        val startId = 100
        if (preKeyStore.containsPreKey(startId)) return
        val records = KeyHelper.generatePreKeys(startId, 100)
        records.forEach { preKeyStore.storePreKey(it.id, it) }
        Log.i(TAG, "Generated ${records.size} one-time prekeys")
    }

    //  PreKeyBundle (what we send to the other side during handshake) 

    fun buildLocalPreKeyBundle(): PreKeyBundle {
        val idPair      = identityStore.getIdentityKeyPair()
        val regId       = identityStore.getLocalRegistrationId()
        val signedPK    = signedPKStore.loadSignedPreKeys().first()
        // Pick any available one-time prekey
        val oneTimeId   = (100..199).firstOrNull { preKeyStore.containsPreKey(it) } ?: 100
        val oneTimePK   = runCatching { preKeyStore.loadPreKey(oneTimeId) }.getOrNull()
        return PreKeyBundle(
            regId, DEVICE_ID,
            oneTimePK?.id ?: 0,
            oneTimePK?.keyPair?.publicKey,
            signedPK.id,
            signedPK.keyPair.publicKey,
            signedPK.signature,
            idPair.publicKey
        )
    }

    //  Handshake 

    /**
     * Build the FXD1:H: SMS payload(s) containing our PreKeyBundle.
     * The caller (SmsSender) sends these as SMS messages to [address].
     */
    suspend fun initiateHandshake(address: String): List<String> {
        val bundle = buildLocalPreKeyBundle()
        val payload = serializePreKeyBundle(bundle)
        val msgId   = generateMsgId()
        val segments = if (payload.size <= 80) {
            listOf(Envelope.encodeSingle(Envelope.TYPE_HANDSHAKE, payload))
        } else {
            Envelope.encodeChunked(Envelope.TYPE_HANDSHAKE, payload, msgId)
        }
        peers.setHandshakeState(address, PeerRegistry.HandshakeState.SENT)
        Log.i(TAG, "Handshake initiated to $address (${segments.size} segment(s))")
        return segments
    }

    /**
     * Process an incoming PreKeyBundle received from [address].
     * Builds a Signal session using their bundle.
     */
    suspend fun processIncomingHandshake(address: String, bundleBytes: ByteArray) {
        val remoteBundle = deserializePreKeyBundle(bundleBytes)
        val remoteAddr   = SignalProtocolAddress(peers.normalise(address), DEVICE_ID)
        val builder      = SessionBuilder(sessionStore, preKeyStore, signedPKStore,
                                          identityStore, remoteAddr)
        builder.process(remoteBundle)
        // Fingerprint for safety-number display
        val fingerprint = remoteBundle.identityKey.fingerprint
        peers.setFingerprint(address, fingerprint)
        peers.setFixdCapable(address, true)
        peers.setHandshakeState(address, PeerRegistry.HandshakeState.ESTABLISHED)
        Log.i(TAG, "Session established with $address | fingerprint=$fingerprint")
    }

    //  Encrypt 

    /**
     * Returns true if we should encrypt outbound messages to [address].
     * Requires: user opted in AND handshake ESTABLISHED.
     */
    suspend fun shouldEncrypt(address: String): Boolean {
        return peers.isEncryptionOptedIn(address) &&
               peers.getHandshakeState(address) == PeerRegistry.HandshakeState.ESTABLISHED
    }

    /**
     * Encrypt [plaintext] for [address].
     * Returns a list of SMS body strings (one per segment) using the FXD1:M: envelope.
     */
    suspend fun encryptMessage(address: String, plaintext: String): List<String> {
        val remoteAddr = SignalProtocolAddress(peers.normalise(address), DEVICE_ID)
        val cipher     = SessionCipher(sessionStore, preKeyStore, signedPKStore,
                                       identityStore, remoteAddr)
        val cipherMsg  = cipher.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        val payload    = cipherMsg.serialize()
        val msgId      = generateMsgId()
        return Envelope.encodeChunked(Envelope.TYPE_MESSAGE, payload, msgId)
    }

    //  Decrypt 

    /**
     * Decrypt a complete FXD1:M: payload [cipherBytes] from [address].
     * Returns plaintext or null on failure.
     */
    suspend fun decryptMessage(address: String, cipherBytes: ByteArray): String? {
        val remoteAddr = SignalProtocolAddress(peers.normalise(address), DEVICE_ID)
        val cipher     = SessionCipher(sessionStore, preKeyStore, signedPKStore,
                                       identityStore, remoteAddr)
        return runCatching {
            val plain = if (cipherBytes[0].toInt() and 0xFF == 0x33) {
                // PreKeySignalMessage (first message after key exchange)
                cipher.decrypt(PreKeySignalMessage(cipherBytes))
            } else {
                // Regular Signal message
                cipher.decrypt(SignalMessage(cipherBytes))
            }
            String(plain, Charsets.UTF_8)
        }.getOrElse {
            Log.e(TAG, "Decryption failed from $address: ${it.message}")
            null
        }
    }

    //  Serialisation helpers 
    // Simple TLV-style: each field prefixed with 1-byte type + 2-byte length

    private fun serializePreKeyBundle(b: PreKeyBundle): ByteArray {
        val fields = mutableListOf<ByteArray>()
        // 0x01 registrationId (4 bytes big-endian)
        val regId = ByteArray(4).also {
            it[0] = (b.registrationId shr 24).toByte()
            it[1] = (b.registrationId shr 16).toByte()
            it[2] = (b.registrationId shr 8).toByte()
            it[3] = b.registrationId.toByte()
        }
        fields.add(tlv(0x01, regId))
        // 0x02 identityKey
        fields.add(tlv(0x02, b.identityKey.serialize()))
        // 0x03 signedPreKeyId (4 bytes)
        val spkId = ByteArray(4).also {
            it[0] = (b.signedPreKeyId shr 24).toByte()
            it[1] = (b.signedPreKeyId shr 16).toByte()
            it[2] = (b.signedPreKeyId shr 8).toByte()
            it[3] = b.signedPreKeyId.toByte()
        }
        fields.add(tlv(0x03, spkId))
        // 0x04 signedPreKeyPublic
        fields.add(tlv(0x04, b.signedPreKey.serialize()))
        // 0x05 signedPreKeySignature
        fields.add(tlv(0x05, b.signedPreKeySignature))
        // 0x06 preKeyId + preKeyPublic (optional)
        b.preKey?.let {
            val pkId = ByteArray(4).also { id ->
                id[0] = (b.preKeyId shr 24).toByte()
                id[1] = (b.preKeyId shr 16).toByte()
                id[2] = (b.preKeyId shr 8).toByte()
                id[3] = b.preKeyId.toByte()
            }
            fields.add(tlv(0x06, pkId + it.serialize()))
        }
        return fields.fold(ByteArray(0)) { acc, f -> acc + f }
    }

    private fun tlv(type: Byte, data: ByteArray): ByteArray {
        val len = data.size
        return byteArrayOf(type, (len shr 8).toByte(), len.toByte()) + data
    }

    private fun tlv(type: Int, data: ByteArray) = tlv(type.toByte(), data)

    private fun deserializePreKeyBundle(bytes: ByteArray): PreKeyBundle {
        var regId     = 0
        var idKey: org.signal.libsignal.protocol.IdentityKey? = null
        var spkId     = 0
        var spkPub: org.signal.libsignal.protocol.ecc.ECPublicKey? = null
        var spkSig: ByteArray? = null
        var pkId      = 0
        var pkPub: org.signal.libsignal.protocol.ecc.ECPublicKey? = null

        var i = 0
        while (i < bytes.size - 2) {
            val type = bytes[i].toInt() and 0xFF
            val len  = ((bytes[i+1].toInt() and 0xFF) shl 8) or (bytes[i+2].toInt() and 0xFF)
            i += 3
            if (i + len > bytes.size) break
            val data = bytes.copyOfRange(i, i + len)
            i += len
            when (type) {
                0x01 -> regId  = (data[0].toInt() and 0xFF shl 24) or
                                 (data[1].toInt() and 0xFF shl 16) or
                                 (data[2].toInt() and 0xFF shl 8)  or
                                 (data[3].toInt() and 0xFF)
                0x02 -> idKey  = org.signal.libsignal.protocol.IdentityKey(data, 0)
                0x03 -> spkId  = (data[0].toInt() and 0xFF shl 24) or
                                 (data[1].toInt() and 0xFF shl 16) or
                                 (data[2].toInt() and 0xFF shl 8)  or
                                 (data[3].toInt() and 0xFF)
                0x04 -> spkPub = Curve.decodePoint(data, 0)
                0x05 -> spkSig = data
                0x06 -> {
                    pkId  = (data[0].toInt() and 0xFF shl 24) or
                            (data[1].toInt() and 0xFF shl 16) or
                            (data[2].toInt() and 0xFF shl 8)  or
                            (data[3].toInt() and 0xFF)
                    pkPub = Curve.decodePoint(data.copyOfRange(4, data.size), 0)
                }
            }
        }
        return PreKeyBundle(
            regId, DEVICE_ID,
            pkId, pkPub,
            spkId, spkPub!!,
            spkSig!!, idKey!!
        )
    }

    private fun generateMsgId(): String {
        val bytes = ByteArray(4)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
