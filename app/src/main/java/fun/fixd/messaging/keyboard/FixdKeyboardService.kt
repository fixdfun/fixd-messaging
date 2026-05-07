package `fun`.fixd.messaging.keyboard

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SpellCheckerSession.SpellCheckerSessionListener
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.getSystemService

class FixdKeyboardService : InputMethodService(), SpellCheckerSessionListener {

    private var spellChecker: SpellCheckerSession? = null
    private var suggestionStrip: LinearLayout? = null
    private var shifted = false
    private var symbols = false

    override fun onCreate() {
        super.onCreate()
        val tsm = getSystemService<TextServicesManager>()
        spellChecker = tsm?.newSpellCheckerSession(null, null, this, true)
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 8, 16, 8)
        }
        val scroll = HorizontalScrollView(this).apply { addView(strip) }
        suggestionStrip = strip
        container.addView(scroll)
        container.addView(buildKeyboard())
        return container
    }

    private fun buildKeyboard(): LinearLayout {
        val rows = if (symbols) symbolRows() else letterRows()
        val parent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.forEach { row ->
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { key ->
                val btn = Button(this).apply {
                    text = if (shifted && !symbols) key.uppercase() else key
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { onKey(key) }
                }
                rowLayout.addView(btn)
            }
            parent.addView(rowLayout)
        }
        val utilRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "" to { shifted = !shifted; refreshKeyboard() },
            "123" to { symbols = !symbols; refreshKeyboard() },
            "" to { onKey("") },
            "space" to { onKey(" ") },
            "" to { currentInputConnection?.deleteSurroundingText(1, 0) },
            "" to { sendDefaultEditorAction(true) },
        ).forEach { (label, action) ->
            val btn = Button(this).apply {
                text = label
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { action() }
            }
            utilRow.addView(btn)
        }
        parent.addView(utilRow)
        return parent
    }

    private fun letterRows(): List<List<String>> = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"),
    )

    private fun symbolRows(): List<List<String>> = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","*","-","+","(",")"),
        listOf("!","\"","'",":",";","/","?"),
    )

    private fun refreshKeyboard() {
        setInputView(onCreateInputView())
    }

    private fun onKey(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (shifted && !symbols) { shifted = false; refreshKeyboard() }
        runSpellCheck()
    }

    private fun runSpellCheck() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        val word = before.takeLastWhile { !it.isWhitespace() }
        if (word.length < 2) { suggestionStrip?.removeAllViews(); return }
        spellChecker?.getSentenceSuggestions(arrayOf(TextInfo(word)), 5)
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {}

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        val strip = suggestionStrip ?: return
        strip.removeAllViews()
        results?.firstOrNull()?.let { sentence ->
            for (i in 0 until sentence.suggestionsCount) {
                val info = sentence.getSuggestionsInfoAt(i)
                for (j in 0 until info.suggestionsCount) {
                    val suggestion = info.getSuggestionAt(j)
                    val tv = TextView(this).apply {
                        text = suggestion
                        setPadding(20, 8, 20, 8)
                        setOnClickListener { applySuggestion(suggestion) }
                    }
                    strip.addView(tv)
                }
            }
        }
    }

    private fun applySuggestion(replacement: String) {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        val word = before.takeLastWhile { !it.isWhitespace() }
        if (word.isEmpty()) return
        ic.deleteSurroundingText(word.length, 0)
        ic.commitText(replacement + " ", 1)
        suggestionStrip?.removeAllViews()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        suggestionStrip?.removeAllViews()
    }

    override fun onDestroy() {
        spellChecker?.close()
        super.onDestroy()
    }
}
