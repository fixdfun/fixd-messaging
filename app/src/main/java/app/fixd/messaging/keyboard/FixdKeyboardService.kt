package app.fixd.messaging.keyboard

import android.content.Context
import app.fixd.messaging.prefs.FixdPrefs
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.textservice.SentenceSuggestionsInfo
import android.view.textservice.SpellCheckerSession
import android.view.textservice.SuggestionsInfo
import android.view.textservice.TextInfo
import android.view.textservice.TextServicesManager
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * Built-in soft keyboard with letters, symbols, and a categorized emoji
 * picker.  Renders a system-spell-checker suggestion strip above the keys
 * and applies tap-to-replace corrections.
 */
class FixdKeyboardService : InputMethodService(), SpellCheckerSession.SpellCheckerSessionListener {

    private enum class Mode { LETTERS, SYMBOLS, EMOJI }

    private var mode = Mode.LETTERS
    private var shift = false
    private var rootContainer: LinearLayout? = null
    private var suggestionStrip: LinearLayout? = null
    private var spellChecker: SpellCheckerSession? = null

    override fun onCreate() {
        super.onCreate()
        runCatching {
            val tsm = getSystemService(Context.TEXT_SERVICES_MANAGER_SERVICE) as? TextServicesManager
            spellChecker = tsm?.newSpellCheckerSession(null, null, this, true)
        }
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
        container.addView(buildBodyView())
        rootContainer = container
        return container
    }

    private fun buildBodyView(): View = when (mode) {
        Mode.EMOJI -> buildEmojiView()
        else -> buildKeyboardView()
    }

    private fun buildKeyboardView(): LinearLayout {
        val parent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = if (mode == Mode.SYMBOLS) symbolRows else letterRows
        rows.forEach { row ->
            val rowLayout = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            row.forEach { ch ->
                val label = if (mode == Mode.LETTERS && shift) ch.uppercase() else ch
                rowLayout.addKey(label) { commit(label) }
            }
            parent.addView(rowLayout)
        }
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        if (mode == Mode.LETTERS) {
            controls.addKey(if (shift) "" else "") { shift = !shift; refresh() }
        }
        controls.addKey(if (mode == Mode.SYMBOLS) "ABC" else "?123") {
            mode = if (mode == Mode.SYMBOLS) Mode.LETTERS else Mode.SYMBOLS
            refresh()
        }
        controls.addKey("\uD83D\uDE00") { mode = Mode.EMOJI; refresh() }
        controls.addKey("space", weight = 4f) { commit(" ") }
        controls.addKey("") {
            currentInputConnection?.deleteSurroundingText(1, 0)
            refreshSuggestions()
        }
        controls.addKey("") { commit("\n") }
        parent.addView(controls)
        return parent
    }

    private fun buildEmojiView(): LinearLayout {
        val parent = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val tabRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val grid = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 600,
            )
            addView(grid)
        }
        fun showCategory(emojis: List<String>) {
            grid.removeAllViews()
            val perRow = 8
            emojis.chunked(perRow).forEach { chunk ->
                val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                chunk.forEach { e -> row.addKey(e) { commit(e) } }
                grid.addView(row)
            }
        }
        emojiCategories.forEach { (label, list) ->
            tabRow.addView(Button(this).apply {
                text = label
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                setOnClickListener { showCategory(list) }
            })
        }
        parent.addView(tabRow)
        parent.addView(scroll)
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addKey("ABC") { mode = Mode.LETTERS; refresh() }
        controls.addKey("space", weight = 4f) { commit(" ") }
        controls.addKey("") { currentInputConnection?.deleteSurroundingText(2, 0) }
        controls.addKey("") { commit("\n") }
        parent.addView(controls)
        val __startIdx = runCatching { FixdPrefs(this).defaultEmojiCategory }.getOrDefault(0).coerceIn(0, emojiCategories.size - 1); showCategory(emojiCategories[__startIdx].second)
        return parent
    }

    private fun LinearLayout.addKey(label: String, weight: Float = 1f, action: () -> Unit) {
        addView(Button(this@FixdKeyboardService).apply {
            text = label
            isAllCaps = false
            ellipsize = TextUtils.TruncateAt.END
            setSingleLine(true)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            setOnClickListener { action() }
        })
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (shift && mode == Mode.LETTERS) { shift = false; refresh() }
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
        runCatching {
            spellChecker?.getSentenceSuggestions(arrayOf(TextInfo(word)), 5)
        }
    }

    override fun onGetSuggestions(results: Array<out SuggestionsInfo>?) { /* legacy path unused */ }

    override fun onGetSentenceSuggestions(results: Array<out SentenceSuggestionsInfo>?) {
        results ?: return
        val strip = suggestionStrip ?: return
        strip.post {
            strip.removeAllViews()
            for (info in results) {
                for (i in 0 until info.suggestionsCount) {
                    val sug = info.getSuggestionsInfoAt(i) ?: continue
                    for (j in 0 until sug.suggestionsCount) {
                        val s = sug.getSuggestionAt(j) ?: continue
                        strip.addView(TextView(this).apply {
                            text = s
                            setPadding(20, 8, 20, 8)
                            setOnClickListener { applySuggestion(s) }
                        })
                    }
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
        listOf("!","\"","'",":",";","/","?"),
    )

    private val emojiCategories: List<Pair<String, List<String>>> = listOf(
        "\uD83D\uDE00" to listOf(
            "\uD83D\uDE00","\uD83D\uDE01","\uD83D\uDE02","\uD83E\uDD23","\uD83D\uDE03","\uD83D\uDE04",
            "\uD83D\uDE05","\uD83D\uDE06","\uD83D\uDE09","\uD83D\uDE0A","\uD83D\uDE0B","\uD83D\uDE0E",
            "\uD83D\uDE0D","\uD83D\uDE18","\uD83D\uDE17","\uD83D\uDE19","\uD83D\uDE1A","\uD83D\uDE42",
            "\uD83E\uDD17","\uD83E\uDD29","\uD83E\uDD14","\uD83D\uDE10","\uD83D\uDE11","\uD83D\uDE36",
            "\uD83D\uDE44","\uD83D\uDE0F","\uD83D\uDE23","\uD83D\uDE25","\uD83D\uDE2A","\uD83D\uDE2D",
            "\uD83D\uDE2C","\uD83D\uDE30","\uD83D\uDE31","\uD83D\uDE33","\uD83D\uDE35","\uD83D\uDE21",
        ),
        "\uD83D\uDC4B" to listOf(
            "\uD83D\uDC4B","\uD83E\uDD1A","\uD83D\uDD90","\u270B","\uD83D\uDD96","\uD83D\uDC4C",
            "\uD83E\uDD0F","\u270C","\uD83E\uDD1E","\uD83E\uDD1F","\uD83E\uDD18","\uD83E\uDD19",
            "\uD83D\uDC48","\uD83D\uDC49","\uD83D\uDC46","\uD83D\uDC47","\u261D","\uD83D\uDC4D",
            "\uD83D\uDC4E","\u270A","\uD83D\uDC4A","\uD83E\uDD1B","\uD83E\uDD1C","\uD83D\uDC4F",
            "\uD83D\uDE4C","\uD83D\uDC50","\uD83E\uDD32","\uD83E\uDD1D","\uD83D\uDE4F","\u270D",
            "\uD83D\uDC85","\uD83E\uDD33","\uD83D\uDCAA","\uD83E\uDDB5","\uD83E\uDDB6","\uD83D\uDC42",
        ),
        "\uD83D\uDC36" to listOf(
            "\uD83D\uDC36","\uD83D\uDC31","\uD83D\uDC2D","\uD83D\uDC39","\uD83D\uDC30","\uD83E\uDD8A",
            "\uD83D\uDC3B","\uD83D\uDC3C","\uD83D\uDC28","\uD83D\uDC2F","\uD83E\uDD81","\uD83D\uDC2E",
            "\uD83D\uDC37","\uD83D\uDC38","\uD83D\uDC35","\uD83D\uDE48","\uD83D\uDC14","\uD83D\uDC27",
            "\uD83D\uDC26","\uD83D\uDC24","\uD83D\uDC23","\uD83E\uDD86","\uD83D\uDC22","\uD83D\uDC0D",
            "\uD83D\uDC32","\uD83D\uDC09","\uD83D\uDC33","\uD83D\uDC0B","\uD83D\uDC2C","\uD83D\uDC1F",
            "\uD83D\uDC20","\uD83D\uDC21","\uD83E\uDD88","\uD83D\uDC19","\uD83D\uDC1A","\uD83E\uDD80",
        ),
        "\uD83C\uDF55" to listOf(
            "\uD83C\uDF4E","\uD83C\uDF4A","\uD83C\uDF4B","\uD83C\uDF4C","\uD83C\uDF49","\uD83C\uDF47",
            "\uD83C\uDF53","\uD83C\uDF52","\uD83C\uDF51","\uD83C\uDF50","\uD83C\uDF4D","\uD83E\uDD5D",
            "\uD83C\uDF45","\uD83C\uDF46","\uD83E\uDD51","\uD83E\uDD55","\uD83C\uDF3D","\uD83C\uDF36",
            "\uD83E\uDD52","\uD83E\uDD66","\uD83E\uDD44","\uD83C\uDF44","\uD83E\uDD5C","\uD83C\uDF30",
            "\uD83C\uDF5E","\uD83E\uDD50","\uD83E\uDD56","\uD83E\uDD68","\uD83E\uDD5E","\uD83E\uDD5A",
            "\uD83C\uDF73","\uD83E\uDD5E","\uD83E\uDD53","\uD83C\uDF54","\uD83C\uDF5F","\uD83C\uDF55",
        ),
        "\u2764" to listOf(
            "\u2764","\uD83E\uDDE1","\uD83D\uDC9B","\uD83D\uDC9A","\uD83D\uDC99","\uD83D\uDC9C",
            "\uD83D\uDDA4","\uD83E\uDD0D","\uD83E\uDD0E","\uD83D\uDC94","\u2763","\uD83D\uDC95",
            "\uD83D\uDC9E","\uD83D\uDC93","\uD83D\uDC97","\uD83D\uDC96","\uD83D\uDC98","\uD83D\uDC9D",
            "\u2728","\uD83C\uDF1F","\u2B50","\uD83C\uDF1F","\uD83D\uDD25","\uD83C\uDF89",
            "\uD83C\uDF8A","\uD83C\uDF81","\uD83C\uDF82","\uD83C\uDF88","\uD83C\uDF80","\uD83C\uDFB5",
            "\u2705","\u274C","\u2B55","\u2757","\u2753","\uD83D\uDCAF",
        ),
    )
}
