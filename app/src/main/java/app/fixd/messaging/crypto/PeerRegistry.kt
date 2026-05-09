package app.fixd.messaging.crypto

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Persists per-contact Fixd capability state and identity fingerprints.
 *
 * Keys are normalised phone numbers (digits only, no spaces/dashes).
 * All state is stored in a dedicated DataStore file ("fixd_peers").
 *
 * Handshake states:
 *   NONE         no handshake initiated
 *   SENT         we sent our PreKeyBundle, awaiting theirs
 *   ESTABLISHED  full session established, ready for E2E messages
 */
private val Context.peerDataStore by preferencesDataStore(name = "fixd_peers")

class PeerRegistry(private val ctx: Context) {

    enum class HandshakeState { NONE, SENT, ESTABLISHED }

    //  Key helpers 

    private fun capableKey(addr: String)       = booleanPreferencesKey("${addr}_capable")
    private fun hsStateKey(addr: String)       = stringPreferencesKey("${addr}_hs_state")
    private fun fingerprintKey(addr: String)   = stringPreferencesKey("${addr}_fingerprint")
    private fun lastHandshakeKey(addr: String) = longPreferencesKey("${addr}_hs_ts")
    private fun encryptOptInKey(addr: String)  = booleanPreferencesKey("${addr}_opt_in")
    private fun chunkBufferKey(addr: String, msgId: String, seq: Int) =
        stringPreferencesKey("${addr}_chunk_${msgId}_$seq")
    private fun chunkTotalKey(addr: String, msgId: String) =
        stringPreferencesKey("${addr}_chunk_${msgId}_total")

    //  Normalise 

    fun normalise(address: String): String =
        address.filter { it.isDigit() || it == '+' }.trimStart('0').let {
            if (it.startsWith("+")) it else "+1$it"   // nave US default; improve in Phase 3
        }

    //  Capability 

    suspend fun isFixdCapable(address: String): Boolean {
        val addr = normalise(address)
        return ctx.peerDataStore.data.map { it[capableKey(addr)] ?: false }.first()
    }

    suspend fun setFixdCapable(address: String, capable: Boolean) {
        val addr = normalise(address)
        ctx.peerDataStore.edit { it[capableKey(addr)] = capable }
    }

    //  Opt-in (user explicitly enabled E2E for this conversation) 

    suspend fun isEncryptionOptedIn(address: String): Boolean {
        val addr = normalise(address)
        return ctx.peerDataStore.data.map { it[encryptOptInKey(addr)] ?: false }.first()
    }

    suspend fun setEncryptionOptIn(address: String, optIn: Boolean) {
        val addr = normalise(address)
        ctx.peerDataStore.edit { it[encryptOptInKey(addr)] = optIn }
    }

    //  Handshake state 

    suspend fun getHandshakeState(address: String): HandshakeState {
        val addr = normalise(address)
        val raw = ctx.peerDataStore.data.map { it[hsStateKey(addr)] }.first()
        return raw?.let { runCatching { HandshakeState.valueOf(it) }.getOrNull() }
            ?: HandshakeState.NONE
    }

    suspend fun setHandshakeState(address: String, state: HandshakeState) {
        val addr = normalise(address)
        ctx.peerDataStore.edit {
            it[hsStateKey(addr)] = state.name
            if (state == HandshakeState.ESTABLISHED) {
                it[lastHandshakeKey(addr)] = System.currentTimeMillis()
            }
        }
    }

    //  Identity fingerprint 

    suspend fun getFingerprint(address: String): String? {
        val addr = normalise(address)
        return ctx.peerDataStore.data.map { it[fingerprintKey(addr)] }.first()
    }

    suspend fun setFingerprint(address: String, fingerprint: String) {
        val addr = normalise(address)
        ctx.peerDataStore.edit { it[fingerprintKey(addr)] = fingerprint }
    }

    //  Chunk buffer (for multi-segment messages) 

    suspend fun storeChunk(address: String, msgId: String, seq: Int, total: Int, b64: String) {
        val addr = normalise(address)
        ctx.peerDataStore.edit {
            it[chunkBufferKey(addr, msgId, seq)] = b64
            it[chunkTotalKey(addr, msgId)] = total.toString()
        }
    }

    suspend fun collectChunks(address: String, msgId: String): Map<Int, String> {
        val addr = normalise(address)
        val prefs = ctx.peerDataStore.data.first()
        val totalStr = prefs[chunkTotalKey(addr, msgId)] ?: return emptyMap()
        val total = totalStr.toIntOrNull() ?: return emptyMap()
        return (1..total).mapNotNull { seq ->
            prefs[chunkBufferKey(addr, msgId, seq)]?.let { seq to it }
        }.toMap()
    }

    suspend fun clearChunks(address: String, msgId: String) {
        val addr = normalise(address)
        val prefs = ctx.peerDataStore.data.first()
        val totalStr = prefs[chunkTotalKey(addr, msgId)] ?: return
        val total = totalStr.toIntOrNull() ?: return
        ctx.peerDataStore.edit { store ->
            (1..total).forEach { seq -> store.remove(chunkBufferKey(addr, msgId, seq)) }
            store.remove(chunkTotalKey(addr, msgId))
        }
    }

    //  Full reset (e.g. "Reset secure session" button) 

    suspend fun resetPeer(address: String) {
        val addr = normalise(address)
        ctx.peerDataStore.edit {
            it.remove(capableKey(addr))
            it.remove(hsStateKey(addr))
            it.remove(fingerprintKey(addr))
            it.remove(lastHandshakeKey(addr))
            // leave opt_in unchanged  user preference
        }
    }
}
