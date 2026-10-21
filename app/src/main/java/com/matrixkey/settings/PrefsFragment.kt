package com.matrixkey.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.inputmethod.InputMethodManager
import androidx.appcompat.app.AlertDialog
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.matrixkey.engine.WordEngine
import java.io.File

/** One settings screen, loaded from the XML file named in its arguments. */
class PrefsFragment : PreferenceFragmentCompat() {

    companion object {
        fun newInstance(xml: String, title: String) = PrefsFragment().apply {
            arguments = Bundle().apply { putString("xml", xml); putString("title", title) }
        }

        private val SECRET_KEYS = setOf("gemini_key", "groq_key", "giphy_key", "klipy_key")
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val name = arguments?.getString("xml") ?: "prefs_main"
        val res = resources.getIdentifier(name, "xml", requireContext().packageName)
        setPreferencesFromResource(res, rootKey)
        // Vibration strength: feel each strength as you slide.
        findPreference<androidx.preference.SeekBarPreference>("vibration_strength")?.let { bar ->
            bar.updatesContinuously = true
            val haptics = com.matrixkey.keyboard.Feedback(requireContext())
            var last = 0L
            var lastValue = -1
            bar.setOnPreferenceChangeListener { _, value ->
                val n = value as Int
                val now = android.os.SystemClock.uptimeMillis()
                if (n != lastValue && now - last > 70) {
                    last = now; lastValue = n
                    view?.let { haptics.preview(it, n) }
                }
                true
            }
        }
        findPreference<Preference>("about")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), AboutActivity::class.java)); true
        }

        // API keys: show only the last 4 characters.
        SECRET_KEYS.forEach { key ->
            findPreference<EditTextPreference>(key)?.let { p ->
                val hint = p.summary?.toString().orEmpty()
                p.setOnBindEditTextListener { it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD; it.setSingleLine() }
                p.summaryProvider = Preference.SummaryProvider<EditTextPreference> { e ->
                    val v = e.text?.trim().orEmpty()
                    if (v.isEmpty()) "Not set · $hint" else "••••••${v.takeLast(4)} · Tap to change"
                }
            }
        }

        findPreference<Preference>("setup_enable")?.setOnPreferenceClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)); true
        }
        findPreference<Preference>("setup_select")?.setOnPreferenceClickListener {
            (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker(); true
        }
        listOf("setup_mic", "voice_mic").forEach { k ->
            findPreference<Preference>(k)?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), PermissionActivity::class.java)); true
            }
        }
        findPreference<Preference>("reset_learned")?.setOnPreferenceClickListener {
            confirm("Reset learned data?", "Learned words, typing habits and touch aim will be cleared. Your personal dictionary stays.") {
                resetLearned(requireContext(), keepPersonal = true)
            }
            true
        }
        findPreference<Preference>("delete_all")?.setOnPreferenceClickListener {
            confirm("Delete all keyboard data?", "This clears your dictionary, clipboard history, imported themes and all settings.") {
                deleteAll(requireContext())
                activity?.recreate()
            }
            true
        }
    }

    override fun onViewCreated(view: android.view.View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        listView.addItemDecoration(CardDecoration(requireContext()) { pos -> flatHeaders().getOrElse(pos) { true } })
        listView.clipToPadding = false
        listView.setPadding(0, 0, 0, (32 * resources.displayMetrics.density).toInt())
        setDivider(null)
        arguments?.getString("highlight")?.let { k -> view.post { highlight(k) } }
    }

    /** Scrolls to a setting and blinks it (used by search). */
    fun highlight(key: String?) {
        if (key.isNullOrEmpty()) return
        scrollToPreference(key)
        val pos = flatKeys().indexOf(key)
        if (pos < 0) return
        listView.postDelayed({
            val v = listView.findViewHolderForAdapterPosition(pos)?.itemView ?: return@postDelayed
            v.animate().alpha(0.25f).setDuration(180).withEndAction {
                v.animate().alpha(1f).setDuration(180).withEndAction {
                    v.animate().alpha(0.25f).setDuration(180).withEndAction { v.animate().alpha(1f).setDuration(220) }
                }
            }
        }, 250)
    }

    private fun flatKeys(): List<String?> {
        val out = ArrayList<String?>()
        fun walk(g: androidx.preference.PreferenceGroup) {
            for (i in 0 until g.preferenceCount) {
                val p = g.getPreference(i)
                if (!p.isVisible) continue
                out += p.key
                if (p is androidx.preference.PreferenceCategory) walk(p)
            }
        }
        preferenceScreen?.let { walk(it) }
        return out
    }

    /** Visible rows in adapter order: true where the row is a section header. */
    private fun flatHeaders(): List<Boolean> {
        val out = ArrayList<Boolean>()
        fun walk(g: androidx.preference.PreferenceGroup) {
            for (i in 0 until g.preferenceCount) {
                val p = g.getPreference(i)
                if (!p.isVisible) continue
                out += p is androidx.preference.PreferenceCategory
                if (p is androidx.preference.PreferenceCategory) walk(p)
            }
        }
        preferenceScreen?.let { walk(it) }
        return out
    }

    override fun onResume() {
        super.onResume()
        updateSetupState()
    }

    private fun updateSetupState() {
        val ctx = context ?: return
        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val enabled = imm.enabledInputMethodList.any { it.packageName == ctx.packageName }
        val current = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty()
        val selected = current.startsWith(ctx.packageName + "/")
        val mic = ctx.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        findPreference<Preference>("setup_enable")?.summary = if (enabled) "Done ✓" else "Enable it in your phone's keyboard list"
        findPreference<Preference>("setup_select")?.summary = if (selected) "Done ✓" else "Pick it as your current keyboard"
        findPreference<Preference>("setup_mic")?.summary = if (mic) "Done ✓" else "Optional · only for voice typing"
        findPreference<Preference>("voice_mic")?.summary = if (mic) "Allowed" else "Tap to allow"
        // The microphone is optional, so setup is complete without it.
        findPreference<Preference>("setup")?.isVisible = !(enabled && selected)
        findPreference<Preference>("about")?.summary =
            "Version " + (runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: "")
    }

    private fun confirm(title: String, msg: String, action: () -> Unit) {
        AlertDialog.Builder(requireContext())
            .setTitle(title).setMessage(msg)
            .setPositiveButton("Delete") { _, _ -> action() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

fun resetLearned(context: Context, keepPersonal: Boolean) {
    val engine = WordEngine.get(context)
    WordEngine.handler.post { engine.ensureLoaded(); engine.resetLearned(keepPersonal) }
    PreferenceManager.getDefaultSharedPreferences(context).edit()
        .remove("aim")
        .putLong("reset_learned_at", System.currentTimeMillis())
        .apply()
}

fun deleteAll(context: Context) {
    val engine = WordEngine.get(context)
    WordEngine.handler.post { engine.ensureLoaded(); engine.resetLearned(keepPersonal = false); File(context.filesDir, "learned.json").delete() }
    File(context.filesDir, "clipboard.json").delete()
    File(context.filesDir, "themes").deleteRecursively()
    File(context.filesDir, "fonts").deleteRecursively()
    File(context.cacheDir, "gifs").deleteRecursively()
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear()
        .putLong("reset_learned_at", System.currentTimeMillis())
        .apply()
}
