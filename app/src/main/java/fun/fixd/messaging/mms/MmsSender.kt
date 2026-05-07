package `fun`.fixd.messaging.mms

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * Minimal m-send-req PDU encoder good enough for single-recipient picture
 * messages on most carriers. We construct the WAP/MMS headers, append the
 * multipart body, write the bytes to app cache, expose them via FileProvider
 * and let SmsManager.sendMultimediaMessage drive the carrier handshake.
 */
class MmsSender(private val context: Context) {

    fun send(to: String, text: String?, attachments: List<Attachment>): Boolean {
        return try {
            val pdu = buildSendReq(to, text, attachments)
            val pduFile = File(context.cacheDir, "mms-${'$'}{UUID.randomUUID()}.dat")
            FileOutputStream(pduFile).use { it.write(pdu) }
            val contentUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${'$'}{context.packageName}.fileprovider",
                pduFile,
            )
            context.grantUriPermission(
                "com.android.mms.service",
                contentUri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            SmsManager.getDefault().sendMultimediaMessage(
                context, contentUri, null, null, null,
            )
            insertOutboxRecord(to, text, attachments)
            true
        } catch (t: Throwable) {
            Log.w("MmsSender", "send failed", t)
            false
        }
    }

    private fun insertOutboxRecord(to: String, text: String?, attachments: List<Attachment>) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(Telephony.Mms.MESSAGE_TYPE, 128) // m-send-req
            put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
            put(Telephony.Mms.READ, 1)
            put(Telephony.Mms.SEEN, 1)
        }
        val mmsUri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values) ?: return
        val mmsId = mmsUri.lastPathSegment ?: return
        // recipient address
        val addrValues = ContentValues().apply {
            put("address", to)
            put("type", 151) // To
            put("charset", 106) // utf-8
        }
        resolver.insert(Uri.parse("content://mms/${'$'}mmsId/addr"), addrValues)
        if (!text.isNullOrEmpty()) {
            val partValues = ContentValues().apply {
                put("mid", mmsId)
                put("ct", "text/plain")
                put("chset", 106)
                put("text", text)
            }
            resolver.insert(Uri.parse("content://mms/${'$'}mmsId/part"), partValues)
        }
        attachments.forEach { att ->
            val partValues = ContentValues().apply {
                put("mid", mmsId)
                put("ct", att.mime)
                put("cl", att.fileName)
                put("_data", att.localPath)
            }
            resolver.insert(Uri.parse("content://mms/${'$'}mmsId/part"), partValues)
        }
    }

    private fun buildSendReq(to: String, text: String?, attachments: List<Attachment>): ByteArray {
        val out = ByteArrayOutputStream()
        // X-Mms-Message-Type = m-send-req (128)
        out.writeShortField(0x8C, 0x80)
        // X-Mms-Transaction-ID
        out.writeStringField(0x98, "T${'$'}{System.currentTimeMillis().toString(16)}")
        // X-Mms-Version 1.2
        out.writeShortField(0x8D, 0x92)
        // From: insert-address-token
        out.write(0xA2.toShort().toInt() and 0xFF) // From header
        out.write(0x02) // length
        out.write(0x81) // value-length 1
        out.write(0x81) // insert-address-token
        // To
        out.writeStringField(0x97, "${'$'}to/TYPE=PLMN")
        // Content-Type: application/vnd.wap.multipart.mixed (0x23)
        out.write(0x84) // Content-Type header
        out.write(0xA3) // value: application/vnd.wap.multipart.related short int
        // Body: multipart
        val parts = mutableListOf<ByteArray>()
        if (!text.isNullOrEmpty()) {
            parts += encodePart("text/plain", "text.txt", text.toByteArray(Charsets.UTF_8))
        }
        attachments.forEach { att ->
            parts += encodePart(att.mime, att.fileName, att.bytes)
        }
        out.writeUintvar(parts.size.toLong())
        parts.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun encodePart(mime: String, name: String, body: ByteArray): ByteArray {
        val headers = ByteArrayOutputStream()
        headers.write(mime.toByteArray(Charsets.US_ASCII))
        headers.write(0)
        headers.writeStringField(0x85, name) // Content-Location
        val headerBytes = headers.toByteArray()
        val out = ByteArrayOutputStream()
        out.writeUintvar(headerBytes.size.toLong())
        out.writeUintvar(body.size.toLong())
        out.write(headerBytes)
        out.write(body)
        return out.toByteArray()
    }

    private fun ByteArrayOutputStream.writeShortField(id: Int, value: Int) {
        write(id)
        write(value)
    }

    private fun ByteArrayOutputStream.writeStringField(id: Int, value: String) {
        write(id)
        write(value.toByteArray(Charsets.UTF_8))
        write(0)
    }

    private fun ByteArrayOutputStream.writeUintvar(value: Long) {
        if (value < 0x80) {
            write(value.toInt())
            return
        }
        val buf = ArrayDeque<Int>()
        var v = value
        buf.addFirst((v and 0x7F).toInt())
        v = v shr 7
        while (v > 0) {
            buf.addFirst(((v and 0x7F) or 0x80).toInt())
            v = v shr 7
        }
        buf.toList().dropLast(1).forEach { write(it or 0x80) }
        write(buf.last())
    }

    data class Attachment(
        val mime: String,
        val fileName: String,
        val localPath: String,
        val bytes: ByteArray,
    )
}
