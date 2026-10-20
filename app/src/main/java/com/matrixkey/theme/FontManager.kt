package com.matrixkey.theme

import android.content.Context
import android.graphics.Typeface
import java.io.File

/**
 * Picks the key font.
 *  - "ndot": Nothing's NDot system font when present, else the bundled Doto
 *    dot-matrix font, else bold monospace.
 *  - "system": the phone's own system font.
 *  - "custom": a font file the person imported in the Theme screen.
 */
object FontManager {
    private var cachedNdot: Typeface? = null
    private var cachedCustom: Pair<Long, Typeface>? = null

    /** True when the key font is a dot-matrix one (drives the dotted icons and trail). */
    fun isDotFont(fontPref: String) = fontPref == "ndot"

    fun keyTypeface(context: Context, fontPref: String): Typeface = when (fontPref) {
        "system" -> Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        "custom" -> custom(context) ?: ndot(context)
        else -> ndot(context)
    }

    fun customFontFile(context: Context) = File(context.filesDir, "fonts/custom_font")

    private fun custom(context: Context): Typeface? {
        val f = customFontFile(context)
        if (!f.exists()) return null
        cachedCustom?.let { if (it.first == f.lastModified()) return it.second }
        return runCatching { Typeface.createFromFile(f) }.getOrNull()?.also {
            cachedCustom = f.lastModified() to it
        }
    }

    fun ndot(context: Context): Typeface {
        cachedNdot?.let { return it }
        val tf = findSystemNdot() ?: bundledDoto(context) ?: Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        cachedNdot = tf
        return tf
    }

    /** Whether Nothing's own NDot font was found on this phone. */
    fun hasSystemNdot(): Boolean = findSystemNdotFile() != null

    private fun findSystemNdotFile(): File? {
        val dirs = listOf("/system/fonts", "/product/fonts", "/system_ext/fonts")
        val candidates = dirs.flatMap { d ->
            File(d).listFiles()?.filter { f ->
                val n = f.name.lowercase()
                n.contains("ndot") && (n.endsWith(".ttf") || n.endsWith(".otf"))
            } ?: emptyList()
        }
        // Prefer the classic NDot 55 cut when several are present.
        return candidates.sortedWith(compareBy({ !it.name.contains("55") }, { it.name.length })).firstOrNull()
    }

    private fun findSystemNdot(): Typeface? =
        findSystemNdotFile()?.let { runCatching { Typeface.createFromFile(it) }.getOrNull() }

    private fun bundledDoto(context: Context): Typeface? = runCatching {
        Typeface.Builder(context.assets, "fonts/Doto.ttf")
            .setFontVariationSettings("'wght' 800")
            .build()
    }.getOrNull()

    fun clearCache() {
        cachedNdot = null
        cachedCustom = null
    }
}
