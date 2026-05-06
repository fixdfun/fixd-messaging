package app.fixd.messaging.keyboard

import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout

/**
 * Simple software keyboard for Fixd Messaging.
 * Provides letters, shift, symbols, emoji shortcuts, backspace and enter.
 */
class FixdKeyboardService : InputMethodService() {

    private var shift = false
    private var symbolMode = false
    private var rootContainer: LinearLayout? = null

    override fun onCreateInputView(): View {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(8, 8, 8, 8)
        }
        rootContainer = container
        rebuildKeys()
        return container
    }

    private fun rebuildKeys() {
        val container = rootContainer ?: return
        container.removeAllViews()
        val rows = if (symbolMode) symbolRows else letterRows
        rows.forEach { row ->
            val rowView = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
            row.forEach { key ->
                rowView.addView(makeKey(key))
            }
            container.addView(rowView)
        }
        // Bottom utility row
        val bottom = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        bottom.addView(makeUtility(if (symbolMode) "ABC" else "123") {
            symbolMode = !symbolMode; rebuildKeys()
        })
        bottom.addView(makeUtility(",") { commit(",") })
        bottom.addView(makeUtility("space", weight = 4f) { commit(" ") })
        bottom.addView(makeUtility(".") { commit(".") })
        bottom.addView(makeUtility("\u232B") {
            currentInputConnection?.deleteSurroundingText(1, 0)
        })
        bottom.addView(makeUtility("\u23CE") {
            val ic = currentInputConnection ?: return@makeUtility
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        })
        container.addView(bottom)
    }

    private fun makeKey(label: String): View {
        return Button(this).apply {
            text = if (shift) label.uppercase() else label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                if (label == "\u21E7") { shift = !shift; rebuildKeys() }
                else commit(if (shift) label.uppercase() else label)
            }
        }
    }

    private fun makeUtility(label: String, weight: Float = 1.5f, onClick: () -> Unit): View {
        return Button(this).apply {
            text = label
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)
            setOnClickListener { onClick() }
        }
    }

    private fun commit(text: String) {
        currentInputConnection?.commitText(text, 1)
        if (shift) { shift = false; rebuildKeys() }
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        symbolMode = (attribute?.inputType?.and(InputType.TYPE_MASK_CLASS) == InputType.TYPE_CLASS_NUMBER)
        shift = false
        rebuildKeys()
    }

    companion object {
        val letterRows = listOf(
            listOf("q","w","e","r","t","y","u","i","o","p"),
            listOf("a","s","d","f","g","h","j","k","l"),
            listOf("\u21E7","z","x","c","v","b","n","m")
        )
        val symbolRows = listOf(
            listOf("1","2","3","4","5","6","7","8","9","0"),
            listOf("@","#","$","%","&","*","-","+","(",")"),
            listOf("!","\"","'",":",";","/","?","\u263A","\u2764")
        )
    }
}
