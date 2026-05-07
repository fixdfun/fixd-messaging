package app.fixd.messaging.prefs

import android.content.Context
import android.content.SharedPreferences

class FixdPrefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.applicationContext.getSharedPreferences("fixd_messaging", Context.MODE_PRIVATE)

    var signature: String
        get() = sp.getString(KEY_SIGNATURE, "") ?: ""
        set(v) { sp.edit().putString(KEY_SIGNATURE, v).apply() }

    var defaultEmojiCategory: Int
        get() = sp.getInt(KEY_EMOJI_CATEGORY, 0)
        set(v) { sp.edit().putInt(KEY_EMOJI_CATEGORY, v).apply() }

    var enterKeySends: Boolean
        get() = sp.getBoolean(KEY_ENTER_SENDS, false)
        set(v) { sp.edit().putBoolean(KEY_ENTER_SENDS, v).apply() }

    companion object {
        private const val KEY_SIGNATURE = "signature"
        private const val KEY_EMOJI_CATEGORY = "emoji_category"
        private const val KEY_ENTER_SENDS = "enter_sends"
        val EMOJI_CATEGORIES = listOf("Smileys", "Gestures", "Animals", "Food", "Hearts")
    }
}
