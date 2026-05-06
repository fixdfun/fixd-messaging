package app.fixd.messaging.mms

import android.content.Context

/** Skeleton MMS sender. Real implementation requires building m-send-req PDUs and
 *  invoking SmsManager.sendMultimediaMessage with the rendered ContentUri. */
object MmsSender {
    fun send(ctx: Context, to: String, subject: String?, parts: List<MmsPart>): Boolean {
        // TODO: Build PDU, write to a content URI, call SmsManager#sendMultimediaMessage
        return false
    }
}

data class MmsPart(val contentType: String, val data: ByteArray, val name: String? = null)
