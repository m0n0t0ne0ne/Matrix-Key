package com.matrixkey.theme

import android.content.Context
import android.graphics.Typeface
import android.graphics.fonts.SystemFonts
import java.io.File

/**
 * Picks the key font. Preference values:
 *  - "ndot":   Nothing's dot font from the phone (NDot 57 preferred), else Doto
 *  - "sys:<path>": one specific Nothing font file found on the phone
 *  - "doto":   the bundled open-source Doto dot-matrix font
 *  - "system": the phone's regular system font
 *  - "custom": a font file imported in the Theme screen
 */
object FontManager {
    private val cache = HashMap<String, Typeface>()

    data class NothingFont(val file: File, val label: String) {
        val prefValue get() = "sys:" + file.absolutePath
        val isDot get() = file.name.lowercase().contains("dot")
    }

    /** True when the key font is a dot-matrix one (drives the dotted icons and trail). */
    fun isDotFont(fontPref: String) = when {
        fontPref == "ndot" || fontPref == "doto" -> true
        fontPref.startsWith("sys:") -> fontPref.lowercase().contains("dot")
        else -> false
    }

    fun keyTypeface(context: Context, fontPref: String): Typeface = when {
        fontPref == "system" -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        fontPref == "custom" -> custom(context) ?: ndot(context)
        fontPref == "doto" -> doto(context)
        fontPref.startsWith("sys:") -> fromFile(File(fontPref.removePrefix("sys:"))) ?: ndot(context)
        else -> ndot(context)
    }

    fun customFontFile(context: Context) = File(context.filesDir, "fonts/custom_font")

    private fun custom(context: Context): Typeface? {
        val f = customFontFile(context)
        if (!f.exists()) return null
        val key = "custom:" + f.lastModified()
        cache[key]?.let { return it }
        return runCatching { Typeface.createFromFile(f) }.getOrNull()?.also { cache[key] = it }
    }

    private fun fromFile(f: File): Typeface? {
        val key = "file:" + f.absolutePath
        cache[key]?.let { return it }
        if (!f.exists()) return null
        return runCatching { Typeface.createFromFile(f) }.getOrNull()?.also { cache[key] = it }
    }

    fun ndot(context: Context): Typeface {
        val best = nothingFonts().filter { it.isDot }
            .sortedWith(compareBy({ !it.file.name.contains("57") }, { it.file.name.length }))
            .firstOrNull()
        return best?.let { fromFile(it.file) } ?: doto(context)
    }

    fun doto(context: Context): Typeface {
        cache["doto"]?.let { return it }
        val tf = runCatching {
            Typeface.Builder(context.assets, "fonts/Doto.ttf").setFontVariationSettings("'wght' 800").build()
        }.getOrNull() ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        cache["doto"] = tf
        return tf
    }

    private var scanned: List<NothingFont>? = null

    /** Every Nothing typeface installed on the phone (NDot, NType, Lettera …). */
    fun nothingFonts(): List<NothingFont> {
        scanned?.let { return it }
        val files = LinkedHashSet<File>()
        runCatching { SystemFonts.getAvailableFonts().mapNotNull { it.file }.forEach { files += it } }
        listOf("/system/fonts", "/product/fonts", "/system_ext/fonts", "/my_product/fonts", "/vendor/fonts").forEach { d ->
            File(d).listFiles()?.let { files.addAll(it) }
        }
        val out = files.filter { f ->
            val n = f.name.lowercase()
            (n.endsWith(".ttf") || n.endsWith(".otf")) &&
                (n.contains("ndot") || n.contains("ntype") || n.contains("nothing") || n.contains("lettera"))
        }.distinctBy { it.name.lowercase() }
            .map { NothingFont(it, prettyName(it.name)) }
            .sortedWith(compareBy({ !it.isDot }, { it.label }))
        scanned = out
        return out
    }

    fun hasSystemNdot(): Boolean = nothingFonts().any { it.isDot }

    private fun prettyName(file: String): String {
        val base = file.substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
        return base.replace(Regex("(?i)ndot"), "NDot ").replace(Regex("(?i)ntype"), "NType ")
            .replace(Regex("\\s+"), " ").trim()
    }

    fun clearCache() {
        cache.clear()
    }
}
