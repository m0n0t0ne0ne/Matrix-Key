package com.matrixkey.panels

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

@SuppressLint("ViewConstructor")
class ClipboardPanel(
    context: Context,
    private val ui: PanelUi,
    private val store: ClipboardStore,
    private val enabled: Boolean,
    private val onPaste: (String) -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val list = ui.column()

    init {
        orientation = VERTICAL
        setPadding(ui.px(8f), ui.px(4f), ui.px(8f), ui.px(4f))

        val header = ui.row()
        header.addView(ui.chip("ABC") { onClose() }, LayoutParams(LayoutParams.WRAP_CONTENT, ui.px(40f)))
        header.addView(ui.gap(12f))
        header.addView(ui.title("Clipboard"), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ui.chip("Clear") { store.clearUnpinned(); refresh() }, LayoutParams(LayoutParams.WRAP_CONTENT, ui.px(40f)))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, ui.px(48f)))

        val scroll = ScrollView(context)
        scroll.addView(list)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
        refresh()
    }

    private fun refresh() {
        list.removeAllViews()
        if (!enabled) {
            list.addView(ui.text("Clipboard history is off. Turn it on in Settings › Clipboard.", 14f, ui.theme.sub).apply {
                setPadding(ui.px(8f), ui.px(16f), ui.px(8f), 0)
            })
            return
        }
        if (store.items.isEmpty()) {
            list.addView(ui.text("Things you copy will show up here. Long-press an item to pin it.", 14f, ui.theme.sub).apply {
                setPadding(ui.px(8f), ui.px(16f), ui.px(8f), 0)
            })
            return
        }
        val sorted = store.items.sortedWith(compareBy({ !it.pinned }, { -it.time }))
        for (item in sorted) {
            val tv = TextView(context).apply {
                text = (if (item.pinned) "📌  " else "") + item.text
                setTextColor(ui.theme.text)
                textSize = 15f
                maxLines = 3
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.px(14f), ui.px(12f), ui.px(14f), ui.px(12f))
                ui.pressable(this, ui.theme.key, 14f)
                setOnClickListener { onPaste(item.text) }
                setOnLongClickListener { store.togglePin(item); refresh(); true }
            }
            list.addView(tv, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, ui.px(4f), 0, ui.px(4f))
            })
        }
    }
}
