package app.fixd.messaging

import android.app.Application
import androidx.emoji2.text.EmojiCompat
import androidx.emoji2.bundled.BundledEmojiCompatConfig

class FixdApp : Application() {
    override fun onCreate() {
        super.onCreate()
        EmojiCompat.init(BundledEmojiCompatConfig(this))
    }
}
