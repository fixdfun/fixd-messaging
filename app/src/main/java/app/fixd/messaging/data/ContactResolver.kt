package app.fixd.messaging.data

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/** Resolves a phone number to a display name via the Contacts provider. */
class ContactResolver(private val context: Context) {
    private val cache = mutableMapOf<String, String>()

    fun displayName(address: String?): String {
        if (address.isNullOrBlank()) return ""
        cache[address]?.let { return it }
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(address),
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0) ?: address
                    cache[address] = name
                    name
                } else {
                    cache[address] = address
                    address
                }
            } ?: address
        } catch (_: SecurityException) {
            address
        }
    }
}
