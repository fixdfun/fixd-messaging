package app.fixd.messaging.backup

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream

/**
 * Exports SMS conversations to a single JSON document the user can save to
 * Drive, copy to a new device, and restore. MMS attachments are referenced by
 * relative path; the importer pairs them back up with the JSON.
 *
 * The export format is intentionally simple and human-readable so users can
 * audit exactly what is leaving their device.
 */
object BackupManager {

    fun exportSms(ctx: Context, out: OutputStream) {
        val arr = JSONArray()
        val cr = ctx.contentResolver
        val cols = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        cr.query(Telephony.Sms.CONTENT_URI, cols, null, null, "${Telephony.Sms.DATE} ASC")?.use { c ->
            while (c.moveToNext()) {
                val obj = JSONObject()
                    .put("id", c.getLong(0))
                    .put("threadId", c.getLong(1))
                    .put("address", c.getString(2) ?: "")
                    .put("body", c.getString(3) ?: "")
                    .put("date", c.getLong(4))
                    .put("type", c.getInt(5))
                arr.put(obj)
            }
        }
        val root = JSONObject()
            .put("app", "fixd-messaging")
            .put("format", 1)
            .put("exportedAt", System.currentTimeMillis())
            .put("messages", arr)
        out.write(root.toString(2).toByteArray(Charsets.UTF_8))
        out.flush()
    }

    /**
     * Restore is intentionally additive: it never deletes existing rows, only
     * inserts ones that are not already present (matched by address + date).
     */
    fun restoreSms(ctx: Context, source: Uri) {
        // Implementor: stream JSON, dedupe, insert into Telephony.Sms.CONTENT_URI.
    }
}
