package app.fixd.messaging.mms

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Single attachment payload for an outgoing MMS. */
data class MmsPart(val contentType: String, val data: ByteArray, val name: String? = null)

/**
 * Encodes a minimal m-send-req PDU with the supplied parts, writes it to app
 * cache, exposes it via FileProvider and asks SmsManager to perform the
 * carrier handshake. Adequate for single-recipient picture messages.
 */
object MmsSender {

    private const val TAG = "MmsSender"

    fun send(ctx: Context, to: String, subject: String?, parts: List<MmsPart>): Boolean {
        return try {
            val pdu = buildSendReq(to, subject, parts)
            val pduFile = File(ctx.cacheDir, "mms-${'$'}{UUID.randomUUID()}.dat")
            FileOutputStream(pduFile).use { it.write(pdu) }
            val contentUri = FileProvider.getUriForFile(
                ctx,
                "${'$'}{ctx.packageName}.fileprovider",
                pduFile,
            )
            ctx.grantUriPermission(
                "com.android.mms.service",
                contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            @Suppress("DEPRECATION")
            SmsManager.getDefault().sendMultimediaMessage(
                ctx, contentUri, null, null, null,
            )
            insertOutboxRecord(ctx, to, subject, parts)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "send failed", t)
            false
        }
    }

    private fun insertOutboxRecord(ctx: Context, to: String, subject: String?, parts: List<MmsPart>) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_TYPE, 128) // m-send-req
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            if (!subject.isNullOrEmpty()) put(Telephony.Mms.SUBJECT, subject)
        }
        val mmsUri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values) ?: return
        val mmsId = mmsUri.lastPathSegment ?: return
        resolver.insert(
            Uri.parse("content://mms/${'$'}mmsId/addr"),
            ContentValues().apply {
                put("address", to)
                put("type", 151) // To
                put("charset", 106)
            },
        )
        parts.forEach { p ->
            resolver.insert(
                Uri.parse("content://mms/${'$'}mmsId/part"),
                ContentValues().apply {
                    put("mid", mmsId)
                    put("ct", p.contentType)
                    if (p.name != null) put("cl", p.name)
                    if (p.contentType.startsWith("text/")) {
                        put("chset", 106)
                        put("text", String(p.data, Charsets.UTF_8))
                    }
                },
            )
        }
    }

    private fun buildSendReq(to: String, subject: String?, parts: List<MmsPart>): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeShortField(0x8C, 0x80)                                   // X-Mms-Message-Type m-send-req
        out.writeStringField(0x98, "T${'$'}{System.currentTimeMillis().toString(16)}") // Transaction-ID
        out.writeShortField(0x8D, 0x92)                                   // X-Mms-Version 1.2
        out.write(0xA2); out.write(0x02); out.write(0x81); out.write(0x81) // From: insert-address-token
        out.writeStringField(0x97, "${'$'}to/TYPE=PLMN")                       // To
        if (!subject.isNullOrEmpty()) out.writeStringField(0x96, subject)
        out.write(0x84); out.write(0xA3)                                  // Content-Type multipart.related
        out.writeUintvar(parts.size.toLong())
        parts.forEach { out.write(encodePart(it)) }
        return out.toByteArray()
    }

    private fun encodePart(p: MmsPart): ByteArray {
        val headers = ByteArrayOutputStream()
        headers.write(p.contentType.toByteArray(Charsets.US_ASCII))
        headers.write(0)
        if (p.name != null) headers.writeStringField(0x85, p.name)
        val headerBytes = headers.toByteArray()
        val out = ByteArrayOutputStream()
        out.writeUintvar(headerBytes.size.toLong())
        out.writeUintvar(p.data.size.toLong())
        out.write(headerBytes)
        out.write(p.data)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShortField(id: Int, value: Int) { write(id); write(value) }
    private fun ByteArrayOutputStream.writeStringField(id: Int, value: String) {
        write(id)
        write(value.toByteArray(Charsets.UTF_8))
        write(0)
    }
    private fun ByteArrayOutputStream.writeUintvar(value: Long) {
        if (value < 0x80) { write(value.toInt()); return }
        val bytes = ArrayDeque<Int>()
        var v = value
        bytes.addFirst((v and 0x7F).toInt())
        v = v shr 7
        while (v > 0) {
            bytes.addFirst(((v and 0x7F) or 0x80).toInt())
            v = v shr 7
        }
        val list = bytes.toList()
        list.dropLast(1).forEach { write(it or 0x80) }
        write(list.last())
    }
}
