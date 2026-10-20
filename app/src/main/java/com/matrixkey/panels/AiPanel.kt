package com.matrixkey.panels

import android.annotation.SuppressLint
import android.content.Context
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.matrixkey.net.AiClient

/**
 * AI writing: pick an action, see the result, then replace your text, try
 * again or cancel. Nothing is sent until an action is tapped.
 */
@SuppressLint("ViewConstructor")
class AiPanel(
    context: Context,
    private val ui: PanelUi,
    private val source: String,
    private val hasSelection: Boolean,
    private val onRun: (AiClient.Action, String) -> Unit,
    /** Opens the prompt line above the keys for a custom instruction or a target language. */
    private val onAskPrompt: (AiClient.Action) -> Unit,
    private val onReplace: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val body = ui.column()
    private var lastAction = AiClient.Action.PROOFREAD
    private var lastExtra = ""

    init {
        orientation = VERTICAL
        setPadding(ui.px(10f), ui.px(4f), ui.px(10f), ui.px(6f))
        val header = ui.row()
        header.addView(ui.chip("ABC") { onClose() }, LayoutParams(LayoutParams.WRAP_CONTENT, ui.px(40f)))
        header.addView(ui.gap(12f))
        header.addView(ui.title("AI writing"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, ui.px(48f)))
        addView(body, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        showChoices()
    }

    private fun preview(text: String, color: Int = ui.theme.text): ScrollView {
        val tv = ui.text(text, 15f, color).apply {
            setPadding(ui.px(14f), ui.px(12f), ui.px(14f), ui.px(12f))
        }
        return ScrollView(context).apply {
            background = ui.rounded(ui.theme.key, 14f)
            addView(tv)
        }
    }

    fun showChoices() {
        body.removeAllViews()
        if (source.isBlank()) {
            body.addView(ui.text("Type something first, then tap the AI button to proofread or rewrite it.", 15f, ui.theme.sub).apply {
                setPadding(ui.px(4f), ui.px(16f), ui.px(4f), 0)
            })
            return
        }
        val what = if (hasSelection) "Selected text" else "Your message"
        body.addView(ui.text(what, 12f, ui.theme.sub), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        body.addView(preview(source, ui.theme.sub), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, ui.px(4f), 0, ui.px(8f)) })

        val actions = listOf(
            AiClient.Action.PROOFREAD, AiClient.Action.FORMAL, AiClient.Action.CASUAL, AiClient.Action.SHORTER,
            AiClient.Action.FRIENDLY, AiClient.Action.EXPAND, AiClient.Action.EMOJIFY, AiClient.Action.TRANSLATE, AiClient.Action.CUSTOM,
        )
        val rowsScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val grid = ui.column()
        val r1 = ui.row(); val r2 = ui.row()
        actions.forEachIndexed { i, a ->
            val label = when (a) { AiClient.Action.CUSTOM -> "✎ Custom"; AiClient.Action.TRANSLATE -> "Translate…"; else -> a.label }
            val chip = ui.chip(label, filled = a == AiClient.Action.PROOFREAD) {
                if (a == AiClient.Action.CUSTOM || a == AiClient.Action.TRANSLATE) onAskPrompt(a) else start(a, "")
            }
            (if (i < 5) r1 else r2).addView(chip, LayoutParams(LayoutParams.WRAP_CONTENT, ui.px(40f)).apply { setMargins(0, ui.px(3f), ui.px(6f), ui.px(3f)) })
        }
        grid.addView(r1); grid.addView(r2)
        rowsScroll.addView(grid)
        body.addView(rowsScroll)
    }

    fun start(action: AiClient.Action, extra: String) {
        lastAction = action; lastExtra = extra
        body.removeAllViews()
        body.addView(ui.text("Working on it…", 15f, ui.theme.sub).apply {
            gravity = Gravity.CENTER
        }, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        onRun(action, extra)
    }

    fun showResult(result: String) {
        body.removeAllViews()
        body.addView(ui.text(lastAction.label + (if (lastExtra.isNotBlank() && lastAction != AiClient.Action.CUSTOM) " → $lastExtra" else ""), 12f, ui.theme.sub))
        body.addView(preview(result), LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f).apply { setMargins(0, ui.px(4f), 0, ui.px(8f)) })
        val buttons = ui.row()
        buttons.addView(ui.chip("Replace", filled = true) { onReplace(result) }, LayoutParams(0, ui.px(44f), 1f).apply { setMargins(0, 0, ui.px(6f), 0) })
        buttons.addView(ui.chip("Try again") { start(lastAction, lastExtra) }, LayoutParams(0, ui.px(44f), 1f).apply { setMargins(0, 0, ui.px(6f), 0) })
        buttons.addView(ui.chip("Back") { showChoices() }, LayoutParams(0, ui.px(44f), 1f))
        body.addView(buttons)
    }

    fun showError(message: String) {
        body.removeAllViews()
        val tv: TextView = ui.text(message, 15f, ui.theme.text).apply { gravity = Gravity.CENTER }
        body.addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        val buttons = ui.row()
        buttons.addView(ui.chip("Try again", filled = true) { start(lastAction, lastExtra) }, LayoutParams(0, ui.px(44f), 1f).apply { setMargins(0, 0, ui.px(6f), 0) })
        buttons.addView(ui.chip("Back") { showChoices() }, LayoutParams(0, ui.px(44f), 1f))
        body.addView(buttons)
    }
}
