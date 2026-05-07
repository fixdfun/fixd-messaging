package app.fixd.messaging.keyboard

import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView

class FixdKeyboardService : InputMethodService(), SpellCheckerSession.SpellCheckerSessionListener {

    private var shift = false
    private var symbolMode = false
    private var rootContainer: LinearLayout? = null
    private var suggestionStrip: LinearLayout? = null
    private var spellChecker: SpellCheckerSession? = null

    override fun onCreate() {
        super.onCreate()
        val tsm = getSystemService(TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
        spellChecker = tsm?.newSpellCheckerSession(null, null, this, true)
    }

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        val strip = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(8, 4, 8, 4)
        }
        suggestionStrip = strip
        container.addView(HorizontalScrollView(this).apply { addView(strip) })
        container.addView(buildKeyboardView())
        rootContainer = container
        return container
    }

    private fun buildKeyboardView(): LinearLayout {
        val parent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = if (symbolMode) symbolRows else letterRows
        rows.forEach { row ->
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { key ->
                val btn = Button(this).apply {
                    text = if (shift && !symbolMode) key.uppercase() else key
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    setOnClickListener { commit(if (shift && !symbolMode) key.uppercase() else key) }
                }
                rowLayout.addView(btn)
            }
            parent.addView(rowLayout)
        }
        val util = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        addUtility(util, "\u21E7") { shift = !shift; refresh() }
        addUtility(util, if (symbolMode) "abc" else "123") { symbolMode = !symbolMode; refresh() }
        addUtility(util, "\uD83D\uDE00") { commit("\uD83D\uDE00") }
        addUtility(util, "space") { commit(" ") }
        addUtility(util, "\u232B") { currentInputConnection?.deleteSurroundingText(1, 0); refreshSuggestions() }
        addUtility(util, "\u23CE") { sendDefaultEditorAction(true) }
        parent.addView(util)
        return parent
    }

    private fun addUtility(parent: LinearLayout, label: String, action: () -> Unit) {
        parent.addView(Button(this).apply {
            text = label
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { action() }
        })
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (shift && !symbolMode) { shift = false; refresh() }
        refreshSuggestions()
    }

    private fun refresh() { setInputView(onCreateInputView()) }

    private fun refreshSuggestions() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(64, 0)?.toString() ?: return
        val word = before.takeLastWhile { !it.isWhitespace() }
        if (word.length < 2) {
            suggestionStrip?.removeAllViews()
            return
        }
        spellChecker?.getSentenceSuggestions(arrayOf(TextInfo(word)), 5)
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) {}

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        val strip = suggestionStrip ?: return
        strip.post {
            strip.removeAllViews()
            val sentence = results?.firstOrNull() ?: return@post
            for (i in 0 until sentence.suggestionsCount) {
                val info = sentence.getSuggestionsInfoAt(i)
                for (j in 0 until info.suggestionsCount) {
                    val suggestion = info.getSuggestionAt(j) ?: continue
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
        ic.commitText("$replacement ", 1)
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

    private val letterRows = listOf(
        listOf("q","w","e","r","t","y","u","i","o","p"),
        listOf("a","s","d","f","g","h","j","k","l"),
        listOf("z","x","c","v","b","n","m"),
    )

    private val symbolRows = listOf(
        listOf("1","2","3","4","5","6","7","8","9","0"),
        listOf("@","#","$","%","&","*","-","+","(",")"),
        listOf("!","\\\"","'",":",";","/","?"),
    )
}
