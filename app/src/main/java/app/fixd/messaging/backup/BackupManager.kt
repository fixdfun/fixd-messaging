package app.fixd.messaging.backup

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Telephony
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.OutputStream

@Serializable
data class SmsBackupRow(
    val address: String?,
    val body: String?,
    val date: Long,
    val type: Int,
    val read: Int
)

@Serializable
data class SmsBackupFile(
    val version: Int = 1,
    val app: String = "fixd-messaging",
    val exported: Long = System.currentTimeMillis(),
    val rows: List<SmsBackupRow>
)

class BackupManager(private val context: Context) {

    suspend fun exportToDownloads(filename: String = "fixd-messaging-backup.json"): Int = withContext(Dispatchers.IO) {
        val rows = readAllSms()
        val payload = SmsBackupFile(rows = rows)
        val json = Json { prettyPrint = false }.encodeToString(SmsBackupFile.serializer(), payload).toByteArray()
        writeToDownloads(filename, json)
        rows.size
    }

    /** FIXD_CLOUD_BACKUP_V1: synchronous JSON snapshot for cloud backup encryption. */
    fun buildJsonString(): String {
        val rows = readAllSms()
        val payload = SmsBackupFile(rows = rows)
        return Json { prettyPrint = false }.encodeToString(SmsBackupFile.serializer(), payload)
    }

    private fun readAllSms(): List<SmsBackupRow> {
        val out = mutableListOf<SmsBackupRow>()
        val proj = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE,
            Telephony.Sms.TYPE, Telephony.Sms.READ)
        runCatching {
            context.contentResolver.query(Telephony.Sms.CONTENT_URI, proj, null, null, "${Telephony.Sms.DATE} ASC")?.use { c ->
                while (c.moveToNext()) {
                    out.add(SmsBackupRow(
                        address = c.getString(0),
                        body = c.getString(1),
                        date = c.getLong(2),
                        type = c.getInt(3),
                        read = c.getInt(4)
                    ))
                }
            }
        }
        return out
    }

    private fun writeToDownloads(filename: String, bytes: ByteArray) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, filename)
                put(MediaStore.Downloads.MIME_TYPE, "application/json")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri).use { it?.write(bytes) }
            values.clear(); values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } else {
            @Suppress("DEPRECATION")
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            dir.mkdirs()
            java.io.File(dir, filename).outputStream().use { it.write(bytes) }
        }
    }

    suspend fun importFromStream(input: java.io.InputStream): Int = withContext(Dispatchers.IO) {
        val text = input.readBytes().toString(Charsets.UTF_8)
        val payload = Json.decodeFromString(SmsBackupFile.serializer(), text)
        var inserted = 0
        payload.rows.forEach { r ->
            // Dedup: skip if (address,date,body) already present
            val exists = runCatching {
                context.contentResolver.query(
                    Telephony.Sms.CONTENT_URI,
                    arrayOf(Telephony.Sms._ID),
                    "${Telephony.Sms.ADDRESS}=? AND ${Telephony.Sms.DATE}=? AND ${Telephony.Sms.BODY}=?",
                    arrayOf(r.address.orEmpty(), r.date.toString(), r.body.orEmpty()),
                    null
                )?.use { it.count > 0 } ?: false
            }.getOrDefault(false)
            if (exists) return@forEach
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, r.address)
                put(Telephony.Sms.BODY, r.body)
                put(Telephony.Sms.DATE, r.date)
                put(Telephony.Sms.TYPE, r.type)
                put(Telephony.Sms.READ, r.read)
            }
            runCatching { context.contentResolver.insert(Telephony.Sms.CONTENT_URI, values) }?.let { inserted++ }
        }
        inserted
    }
}
