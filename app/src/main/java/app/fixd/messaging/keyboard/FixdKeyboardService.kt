package app.fixd.messaging.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Built-in soft keyboard for Fixd Messaging.
 *
 * v0 ships a minimal QWERTY layout to prove the IME wiring; the full layout,
 * suggestion strip (autocorrect), and emoji panel live in subsequent commits.
 */
class FixdKeyboardService : InputMethodService() {

    private val rows = listOf(
        "qwertyuiop".toList(),
        "asdfghjkl".toList(),
        "zxcvbnm".toList()
    )

    override fun onCreateInputView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        // Suggestion / emoji shortcut strip
        val strip = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("\uD83D\uDE00", "\uD83D\uDC4D", "\u2764\uFE0F", "\uD83D\uDD25", "\uD83D\uDE02").forEach { e ->
            strip.addView(emojiButton(e))
        }
        root.addView(strip)
        for (row in rows) {
            val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (ch in row) r.addView(keyButton(ch.toString()))
            root.addView(r)
        }
        // Bottom row: space + return
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        bottom.addView(keyButton(" ", weight = 4f))
        bottom.addView(Button(this).apply {
            text = ""
            setOnClickListener {
                currentInputConnection?.performEditorAction(EditorInfo.IME_ACTION_SEND)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(bottom)
        return root
    }

    private fun keyButton(label: String, weight: Float = 1f): Button = Button(this).apply {
        text = label
        setOnClickListener { currentInputConnection?.commitText(label, 1) }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
    }

    private fun emojiButton(emoji: String): TextView = Button(this).apply {
        text = emoji
        setOnClickListener { currentInputConnection?.commitText(emoji, 1) }
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
    }
}
