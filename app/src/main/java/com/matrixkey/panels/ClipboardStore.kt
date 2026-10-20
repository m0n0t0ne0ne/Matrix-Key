package com.matrixkey.panels

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Clipboard history kept in app storage. Pinned items never expire. */
class ClipboardStore(private val context: Context) {
    data class Item(val text: String, val time: Long, var pinned: Boolean)

    private val file get() = File(context.filesDir, "clipboard.json")
    val items = ArrayList<Item>()

    init { load() }

    private fun load() {
        items.clear()
        runCatching {
            val a = JSONArray(file.readText())
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                items += Item(o.getString("t"), o.getLong("at"), o.optBoolean("p"))
            }
        }
    }

    private fun save() {
        val a = JSONArray()
        items.forEach { a.put(JSONObject().put("t", it.text).put("at", it.time).put("p", it.pinned)) }
        runCatching { file.writeText(a.toString()) }
    }

    /** Reads the current clip; call from the clipboard-changed callback. */
    fun capture(cm: ClipboardManager, hideSensitive: Boolean) {
        val clip = cm.primaryClip ?: return
        if (hideSensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val sensitive = clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE) == true
            if (sensitive) return
        }
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim() ?: return
        if (text.isEmpty() || text.length > 5000) return
        if (hideSensitive && looksLikeOneTimeCode(text)) return
        val existing = items.indexOfFirst { it.text == text }
        val pinned = existing >= 0 && items[existing].pinned
        if (existing >= 0) items.removeAt(existing)
        items.add(0, Item(text, System.currentTimeMillis(), pinned))
        while (items.count { !it.pinned } > 40) items.removeAt(items.indexOfLast { !it.pinned })
        save()
    }

    private fun looksLikeOneTimeCode(t: String) = t.length in 4..8 && t.all { it.isDigit() }

    fun prune(maxAgeMs: Long) {
        if (maxAgeMs <= 0) return
        val cutoff = System.currentTimeMillis() - maxAgeMs
        if (items.removeAll { !it.pinned && it.time < cutoff }) save()
    }

    fun togglePin(item: Item) { item.pinned = !item.pinned; save() }
    fun remove(item: Item) { items.remove(item); save() }
    fun clearUnpinned() { items.removeAll { !it.pinned }; save() }
    fun clearAll() { items.clear(); save() }
}
