package com.matrixkey.settings

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import com.google.android.material.color.MaterialColors
import com.matrixkey.engine.WordEngine
import java.util.concurrent.Executors

/** Personal dictionary and learned words: view, add, remove, import, export. */
class DictionaryActivity : AppCompatActivity() {

    private lateinit var engine: WordEngine
    private lateinit var list: LinearLayout
    private val io = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()

    private val textColor get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurface, Color.BLACK)
    private val subColor get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.GRAY)
    private val primary get() = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.BLUE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = dotTitle(this, "Dictionary")
        engine = WordEngine(this)

        val scroll = ScrollView(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), px(8f), px(16f), px(24f))
        }
        scroll.addView(col)
        val (pageTop, _) = pageHeader(this, "Dictionary", showBack = true) { finish() }
        pageTop.setPadding(0, pageTop.paddingTop, 0, 0)
        col.addView(pageTop)

        val addRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val input = EditText(this).apply {
            hint = "Add a word (names, slang…)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setSingleLine()
        }
        val add = Button(this).apply {
            text = "Add"
            setOnClickListener {
                val w = input.text.toString().trim()
                if (w.isNotEmpty() && !w.contains(' ')) {
                    io.execute { engine.addPersonal(w); notifyService(); main.post { input.setText(""); render() } }
                }
            }
        }
        addRow.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addRow.addView(add)
        col.addView(addRow)

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setPadding(0, px(8f), 0, 0) }
        tools.addView(Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Export"; setOnClickListener { export() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8f) })
        tools.addView(Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
            text = "Import"; setOnClickListener { import() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(tools)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(list)
        col.addView(TextView(this).apply {
            text = "Words you type often are learned automatically after you've used them twice. Long-press a suggestion on the keyboard to stop suggesting it."
            setTextColor(subColor); textSize = 13f
            setPadding(px(4f), px(20f), px(4f), 0)
        })

        setContentView(scroll)
        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            v.updatePadding(bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom); insets
        }

        list.addView(TextView(this).apply { text = "Loading…"; setTextColor(subColor); setPadding(px(4f), px(16f), 0, 0) })
        io.execute { engine.load(); main.post { render() } }
    }

    override fun onSupportNavigateUp(): Boolean { finish(); return true }

    override fun onDestroy() {
        io.shutdown()
        super.onDestroy()
    }

    private fun notifyService() {
        PreferenceManager.getDefaultSharedPreferences(this).edit().putLong("dict_changed_at", System.currentTimeMillis()).apply()
    }

    private fun section(title: String) = TextView(this).apply {
        text = title
        setTextColor(primary)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setPadding(px(4f), px(22f), 0, px(6f))
    }

    private fun render() {
        list.removeAllViews()
        val personal = engine.personalWords().sorted()
        list.addView(section("Personal dictionary (${personal.size})"))
        if (personal.isEmpty()) list.addView(note("No words yet"))
        personal.forEach { list.addView(wordRow(it, null)) }

        val learned = engine.learnedWords()
        list.addView(section("Learned from your typing (${learned.size})"))
        if (learned.isEmpty()) list.addView(note("Nothing learned yet"))
        learned.take(300).forEach { (w, n) -> list.addView(wordRow(w, "used $n×")) }
    }

    private fun note(s: String) = TextView(this).apply { text = s; setTextColor(subColor); setPadding(px(4f), px(4f), 0, px(4f)) }

    private fun wordRow(word: String, meta: String?) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = px(52f)
        addView(TextView(context).apply {
            text = word; setTextColor(textColor); setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (meta != null) addView(TextView(context).apply { text = meta; setTextColor(subColor); textSize = 13f; setPadding(0, 0, px(8f), 0) })
        addView(Button(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
            text = "Remove"
            setOnClickListener { io.execute { engine.removeWord(word); notifyService(); main.post { render() } } }
        })
    }

    // ---- Import / export -------------------------------------------------

    private val EXPORT_REQ = 51
    private val IMPORT_REQ = 52

    private fun export() {
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, "matrix-key-dictionary.json")
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, EXPORT_REQ)
    }

    private fun import() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        @Suppress("DEPRECATION")
        startActivityForResult(i, IMPORT_REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != Activity.RESULT_OK) return
        io.execute {
            val msg = runCatching {
                if (requestCode == EXPORT_REQ) {
                    contentResolver.openOutputStream(uri, "wt")?.use { it.write(engine.exportJson().toByteArray()) }
                    "Exported"
                } else {
                    val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
                    engine.importJson(text)
                    notifyService()
                    "Imported"
                }
            }.getOrElse { "Couldn't read that file" }
            main.post { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show(); render() }
        }
    }
}
