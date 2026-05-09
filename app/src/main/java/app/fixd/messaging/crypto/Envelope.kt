package app.fixd.messaging.crypto

import android.util.Base64

/**
 * FXD1 wire-format encoder / decoder.
 *
 * Every Fixd-encrypted SMS starts with a magic prefix so any device can
 * distinguish it from plain SMS without attempting decryption.
 *
 * Single-segment format (fits in one SMS):
 *   FXD1:<type>:<base64-payload>
 *
 * Multi-segment format (payload too large for one SMS part):
 *   FXD1:<type>:<msgId>:<seq>/<total>:<base64-chunk>
 *
 * Types:
 *   H   Handshake / PreKeyBundle exchange
 *   M   Encrypted message ciphertext
 *   K   Key-update / forced re-handshake
 *
 * Non-Fixd recipients receive the raw text starting with "FXD1:" which is
 * visually obvious but harmless. We gate sending these behind the opt-in
 * per-conversation toggle so regular contacts are never affected.
 */
object Envelope {

    const val PREFIX       = "FXD1"
    const val TYPE_HANDSHAKE = "H"
    const val TYPE_MESSAGE   = "M"
    const val TYPE_REKEY     = "K"

    /** Max Base64 chars per SMS segment, leaving room for the header overhead. */
    private const val CHUNK_B64_SIZE = 100

    //  Encoding 

    /** Encode a single payload that fits in one SMS. */
    fun encodeSingle(type: String, payload: ByteArray): String {
        val b64 = Base64.encodeToString(payload, Base64.NO_WRAP)
        return "$PREFIX:$type:$b64"
    }

    /**
     * Encode a payload that may need multiple SMS segments.
     * Returns one string per SMS segment; each segment is self-describing.
     * [msgId] must be a short unique id (hex, 8 chars) so the receiver can
     * group segments belonging to the same logical message.
     */
    fun encodeChunked(type: String, payload: ByteArray, msgId: String): List<String> {
        val b64Full = Base64.encodeToString(payload, Base64.NO_WRAP)
        val chunks = b64Full.chunked(CHUNK_B64_SIZE)
        val total = chunks.size
        return chunks.mapIndexed { idx, chunk ->
            "$PREFIX:$type:$msgId:${idx + 1}/$total:$chunk"
        }
    }

    //  Decoding 

    sealed class ParseResult {
        /** A complete, single-segment envelope ready to process. */
        data class Complete(val type: String, val payload: ByteArray) : ParseResult()
        /** One chunk of a multi-segment envelope; accumulate until all arrive. */
        data class Chunk(
            val type: String,
            val msgId: String,
            val seq: Int,
            val total: Int,
            val b64Chunk: String
        ) : ParseResult()
        /** Not a Fixd envelope  treat as plain SMS. */
        object NotFixd : ParseResult()
        /** Recognised as Fixd but malformed; log and discard. */
        data class Malformed(val reason: String) : ParseResult()
    }

    fun parse(smsBody: String): ParseResult {
        if (!smsBody.startsWith("$PREFIX:")) return ParseResult.NotFixd
        val parts = smsBody.split(":")
        // Minimum: FXD1 : type : payload   3 parts
        if (parts.size < 3) return ParseResult.Malformed("too few segments")
        val type = parts[1]
        return when (parts.size) {
            3 -> {
                // Single segment: FXD1:T:b64
                runCatching {
                    val payload = Base64.decode(parts[2], Base64.NO_WRAP)
                    ParseResult.Complete(type, payload)
                }.getOrElse { ParseResult.Malformed("base64 decode failed: ${it.message}") }
            }
            5 -> {
                // Chunked: FXD1:T:msgId:seq/total:b64chunk
                val msgId = parts[2]
                val seqTotal = parts[3].split("/")
                if (seqTotal.size != 2) return ParseResult.Malformed("bad seq/total")
                val seq = seqTotal[0].toIntOrNull() ?: return ParseResult.Malformed("seq NaN")
                val total = seqTotal[1].toIntOrNull() ?: return ParseResult.Malformed("total NaN")
                ParseResult.Chunk(type, msgId, seq, total, parts[4])
            }
            else -> ParseResult.Malformed("unexpected segment count: ${parts.size}")
        }
    }

    /**
     * Reassemble a complete payload from accumulated chunks.
     * [chunks] must be a map of seq (1-based)  b64Chunk string.
     * Returns null if any chunk is missing.
     */
    fun reassemble(chunks: Map<Int, String>, total: Int): ByteArray? {
        if (chunks.size < total) return null
        val b64Full = (1..total).joinToString("") { seq ->
            chunks[seq] ?: return null
        }
        return runCatching { Base64.decode(b64Full, Base64.NO_WRAP) }.getOrNull()
    }

    /** Quick check  is this SMS from Fixd? */
    fun isFixdEnvelope(smsBody: String) = smsBody.startsWith("$PREFIX:")
}
