package app.fixd.messaging.crypto

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.Curve
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyStore
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SessionStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyStore

// DataStore instance for all Signal crypto material (separate from peer registry)
private val Context.signalDataStore by preferencesDataStore(name = "fixd_signal_keys")

private fun b64enc(bytes: ByteArray) = Base64.encodeToString(bytes, Base64.NO_WRAP)
private fun b64dec(s: String) = Base64.decode(s, Base64.NO_WRAP)

//  IdentityKeyStore 

class FixdIdentityKeyStore(private val ctx: Context) : IdentityKeyStore {

    private val KEY_IDENTITY_PUBLIC  = stringPreferencesKey("identity_pub")
    private val KEY_IDENTITY_PRIVATE = stringPreferencesKey("identity_priv")
    private val KEY_REG_ID           = intPreferencesKey("registration_id")

    private fun trustedKey(addr: String) = stringPreferencesKey("trusted_${addr}")

    /** Lazy-init: generate a new identity key pair on first use. */
    override fun getIdentityKeyPair(): IdentityKeyPair = runBlocking {
        val prefs = ctx.signalDataStore.data.first()
        val pub  = prefs[KEY_IDENTITY_PUBLIC]
        val priv = prefs[KEY_IDENTITY_PRIVATE]
        if (pub != null && priv != null) {
            val pubKey  = Curve.decodePoint(b64dec(pub), 0)
            val privKey = Curve.decodePrivatePoint(b64dec(priv))
            IdentityKeyPair(IdentityKey(pubKey), privKey)
        } else {
            val pair = Curve.generateKeyPair()
            ctx.signalDataStore.edit {
                it[KEY_IDENTITY_PUBLIC]  = b64enc(pair.publicKey.serialize())
                it[KEY_IDENTITY_PRIVATE] = b64enc(pair.privateKey.serialize())
            }
            IdentityKeyPair(IdentityKey(pair.publicKey), pair.privateKey)
        }
    }

    override fun getLocalRegistrationId(): Int = runBlocking {
        val prefs = ctx.signalDataStore.data.first()
        prefs[KEY_REG_ID] ?: run {
            val id = (1..16380).random()
            ctx.signalDataStore.edit { it[KEY_REG_ID] = id }
            id
        }
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): Boolean =
        runBlocking {
            val key = trustedKey(address.toString())
            val existing = ctx.signalDataStore.data.map { it[key] }.first()
            ctx.signalDataStore.edit { it[key] = b64enc(identityKey.serialize()) }
            existing != null && existing != b64enc(identityKey.serialize())
        }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean = runBlocking {
        val key = trustedKey(address.toString())
        val stored = ctx.signalDataStore.data.map { it[key] }.first()
        stored == null || stored == b64enc(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = runBlocking {
        val key = trustedKey(address.toString())
        ctx.signalDataStore.data.map { it[key] }.first()?.let {
            IdentityKey(b64dec(it), 0)
        }
    }
}

//  PreKeyStore 

class FixdPreKeyStore(private val ctx: Context) : PreKeyStore {

    private fun preKeyKey(id: Int) = stringPreferencesKey("prekey_$id")

    override fun loadPreKey(preKeyId: Int): PreKeyRecord = runBlocking {
        val key = preKeyKey(preKeyId)
        val data = ctx.signalDataStore.data.map { it[key] }.first()
            ?: throw InvalidKeyIdException("No prekey for id=$preKeyId")
        PreKeyRecord(b64dec(data))
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord): Unit = runBlocking {
        ctx.signalDataStore.edit { it[preKeyKey(preKeyId)] = b64enc(record.serialize()) }
    }

    override fun containsPreKey(preKeyId: Int): Boolean = runBlocking {
        ctx.signalDataStore.data.map { it[preKeyKey(preKeyId)] != null }.first()
    }

    override fun removePreKey(preKeyId: Int): Unit = runBlocking {
        ctx.signalDataStore.edit { it.remove(preKeyKey(preKeyId)) }
    }
}

//  SignedPreKeyStore 

class FixdSignedPreKeyStore(private val ctx: Context) : SignedPreKeyStore {

    private fun signedPreKeyKey(id: Int) = stringPreferencesKey("signed_prekey_$id")

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord = runBlocking {
        val key = signedPreKeyKey(signedPreKeyId)
        val data = ctx.signalDataStore.data.map { it[key] }.first()
            ?: throw InvalidKeyIdException("No signed prekey for id=$signedPreKeyId")
        SignedPreKeyRecord(b64dec(data))
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = runBlocking {
        val prefs = ctx.signalDataStore.data.first()
        prefs.asMap().entries
            .filter { it.key.name.startsWith("signed_prekey_") }
            .mapNotNull { entry -> runCatching { SignedPreKeyRecord(b64dec(entry.value as String)) }.getOrNull() }
            .toMutableList()
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord): Unit = runBlocking {
        ctx.signalDataStore.edit { it[signedPreKeyKey(signedPreKeyId)] = b64enc(record.serialize()) }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = runBlocking {
        ctx.signalDataStore.data.map { it[signedPreKeyKey(signedPreKeyId)] != null }.first()
    }

    override fun removeSignedPreKey(signedPreKeyId: Int): Unit = runBlocking {
        ctx.signalDataStore.edit { it.remove(signedPreKeyKey(signedPreKeyId)) }
    }
}

//  SessionStore 

class FixdSessionStore(private val ctx: Context) : SessionStore {

    private fun sessionKey(address: SignalProtocolAddress) =
        stringPreferencesKey("session_${address.name}_${address.deviceId}")

    override fun loadSession(address: SignalProtocolAddress): SessionRecord = runBlocking {
        val key = sessionKey(address)
        val data = ctx.signalDataStore.data.map { it[key] }.first()
        if (data != null) SessionRecord(b64dec(data)) else SessionRecord()
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> = runBlocking {
        val prefs = ctx.signalDataStore.data.first()
        prefs.asMap().keys
            .filter { it.name.startsWith("session_${name}_") }
            .mapNotNull { it.name.substringAfterLast("_").toIntOrNull() }
            .toMutableList()
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord): Unit = runBlocking {
        ctx.signalDataStore.edit { it[sessionKey(address)] = b64enc(record.serialize()) }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean = runBlocking {
        ctx.signalDataStore.data.map { it[sessionKey(address)] != null }.first()
    }

    override fun deleteSession(address: SignalProtocolAddress): Unit = runBlocking {
        ctx.signalDataStore.edit { it.remove(sessionKey(address)) }
    }

    override fun deleteAllSessions(name: String): Unit = runBlocking {
        val prefs = ctx.signalDataStore.data.first()
        val keysToRemove = prefs.asMap().keys.filter { it.name.startsWith("session_${name}_") }
        ctx.signalDataStore.edit { store -> keysToRemove.forEach { store.remove(it) } }
    }
}
