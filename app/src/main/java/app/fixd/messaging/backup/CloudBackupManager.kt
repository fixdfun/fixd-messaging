package app.fixd.messaging.backup

import android.content.Context
import android.util.Base64
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.TimeUnit

/**
 * CloudBackupManager: encrypts a JSON backup with AES-256-GCM (PBKDF2-derived key from user passphrase)
 * and uploads it to https://fixd.fun/api/backup over TLS.
 *
 * Wire format on the server side:
 *   POST multipart/form-data
 *     - email:       String (account email)
 *     - device_id:   String (Settings.Secure.ANDROID_ID)
 *     - file:        ciphertext bytes (.fxbk extension)
 *     - salt_hex:    32-char PBKDF2 salt
 *     - iv_hex:      24-char GCM IV
 *     - iter:        PBKDF2 iteration count
 *     - schema:      "v1"
 *
 * Passphrase NEVER leaves the device. Only ciphertext + salt + iv are uploaded so the user
 * is the sole decryptor. Lost passphrase = lost backup (zero-knowledge).
 */
object CloudBackupManager {
    private const val ENDPOINT = "https://fixd.fun/api/backup"
    private const val ITER = 200_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128

    data class Result(val ok: Boolean, val message: String)

    fun upload(ctx: Context, email: String, passphrase: CharArray, jsonPlaintext: ByteArray): Result {
        return try {
            val rng = SecureRandom()
            val salt = ByteArray(16).also { rng.nextBytes(it) }
            val iv = ByteArray(12).also { rng.nextBytes(it) }
            val key = deriveKey(passphrase, salt)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            val ct = cipher.doFinal(jsonPlaintext)
            val deviceId = android.provider.Settings.Secure.getString(ctx.contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "unknown"
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            val body = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("email", email)
                .addFormDataPart("device_id", deviceId)
                .addFormDataPart("schema", "v1")
                .addFormDataPart("iter", ITER.toString())
                .addFormDataPart("salt_hex", salt.toHex())
                .addFormDataPart("iv_hex", iv.toHex())
                .addFormDataPart("file", "backup.fxbk", ct.toRequestBody("application/octet-stream".toMediaTypeOrNull()))
                .build()
            val req = Request.Builder().url(ENDPOINT).post(body).header("User-Agent", "FixdMessaging/1.0").build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) Result(true, "Backup uploaded (${ct.size} bytes encrypted).")
                else Result(false, "Server returned ${resp.code}: ${resp.message}")
            }
        } catch (e: Exception) {
            Result(false, "Upload failed: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            // wipe passphrase from memory ASAP
            for (i in passphrase.indices) passphrase[i] = '\u0000'
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec: KeySpec = PBEKeySpec(passphrase, salt, ITER, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        return SecretKeySpec(raw, "AES")
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
}
