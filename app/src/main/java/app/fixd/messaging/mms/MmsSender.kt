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
import java.util.ArrayDeque
import java.util.UUID
import kotlin.text.Charsets

data class MmsPart(val contentType: String, val data: ByteArray, val name: String? = null)

/**
 * Encodes an m-send-req PDU with the supplied parts, writes it to the app
 * cache, exposes it via FileProvider and asks SmsManager to perform the
 * carrier handshake.  Supports single- and multi-recipient picture messages
 * and auto-prepends a SMIL layout part when absent.
 */
object MmsSender {

    private const val TAG = "MmsSender"

    /** Backwards-compatible single-recipient entry point. */
    fun send(ctx: Context, to: String, subject: String?, parts: List<MmsPart>): Boolean =
        send(ctx, listOf(to), subject, parts)

    fun send(ctx: Context, recipients: List<String>, subject: String?, parts: List<MmsPart>): Boolean {
        if (recipients.isEmpty()) return false
        val finalParts = ensureSmilPart(parts)
        return try {
            val pdu = buildSendReq(recipients, subject, finalParts)
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
            insertOutboxRecord(ctx, recipients.joinToString(";"), subject, finalParts)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "send failed", t)
            false
        }
    }

    private fun ensureSmilPart(parts: List<MmsPart>): List<MmsPart> {
        if (parts.any { it.contentType == "application/smil" }) return parts
        val sb = StringBuilder()
        sb.append("<smil><head><layout>")
        sb.append("<root-layout/>")
        sb.append("<region id=\"Image\" top=\"0\" left=\"0\" height=\"60%\" width=\"100%\"/>")
        sb.append("<region id=\"Text\" top=\"60%\" left=\"0\" height=\"40%\" width=\"100%\"/>")
        sb.append("</layout></head><body>")
        parts.forEachIndexed { i, p ->
            when {
                p.contentType.startsWith("image/") ->
                    sb.append("<par dur=\"5s\"><img src=\"part").append(i).append("\" region=\"Image\"/></par>")
                p.contentType.startsWith("text/") ->
                    sb.append("<par dur=\"5s\"><text src=\"part").append(i).append("\" region=\"Text\"/></par>")
                else -> { /* skip for SMIL */ }
            }
        }
        sb.append("</body></smil>")
        val smil = sb.toString()
        return listOf(MmsPart("application/smil", smil.toByteArray(Charsets.UTF_8), "smil.xml")) + parts
    }

    private fun insertOutboxRecord(ctx: Context, to: String, subject: String?, parts: List<MmsPart>) {
        val resolver = ctx.contentResolver
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_TYPE, 128) // m-send-req
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
            put(Telephony.Mms.DATE, System.currentTimeMillis() / 1000)
            if (!subject.isNullOrEmpty()) put(Telephony.Mms.SUBJECT, subject)
        }
        val mmsUri = resolver.insert(Telephony.Mms.CONTENT_URI, values) ?: return
        val mmsId = mmsUri.lastPathSegment ?: return
        for (recipient in to.split(";").filter { it.isNotBlank() }) {
            val addrUri = Uri.parse("content://mms/${'$'}mmsId/addr")
            resolver.insert(addrUri, ContentValues().apply {
                put("address", recipient)
                put("type", 151) // PduHeaders.TO
                put("charset", 106)
            })
        }
        val partUri = Uri.parse("content://mms/${'$'}mmsId/part")
        for (p in parts) {
            resolver.insert(partUri, ContentValues().apply {
                put("mid", mmsId)
                put("ct", p.contentType)
                if (p.name != null) put("cl", p.name)
                if (p.contentType.startsWith("text/")) {
                    put("chset", 106)
                    put("text", String(p.data, Charsets.UTF_8))
                }
            })
        }
    }

    private fun buildSendReq(recipients: List<String>, subject: String?, parts: List<MmsPart>): ByteArray {
        val out = ByteArrayOutputStream()
        out.writeShortField(0x8C, 0x80)                              // X-Mms-Message-Type m-send-req
        out.writeStringField(0x98, "T${'$'}{System.currentTimeMillis().toString(16)}") // Transaction-ID
        out.writeShortField(0x8D, 0x92)                              // X-Mms-Version 1.2
        out.write(0xA2); out.write(0x02); out.write(0x81); out.write(0x81) // From: insert-address-token
        for (r in recipients) {
            out.writeStringField(0x97, "${'$'}r/TYPE=PLMN")          // To (repeatable)
        }
        if (!subject.isNullOrEmpty()) out.writeStringField(0x96, subject)
        out.write(0x84); out.write(0xA3)                             // Content-Type multipart.related
        out.writeUintvar(parts.size.toLong())
        parts.forEach { out.write(encodePart(it)) }
        return out.toByteArray()
    }

    private fun encodePart(p: MmsPart): ByteArray {
        val out = ByteArrayOutputStream()
        val headers = ByteArrayOutputStream()
        headers.write(0x8E); headers.write(p.contentType.toByteArray(Charsets.UTF_8)); headers.write(0)
        if (p.name != null) {
            headers.write(0x85); headers.write(p.name.toByteArray(Charsets.UTF_8)); headers.write(0)
        }
        val headerBytes = headers.toByteArray()
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
