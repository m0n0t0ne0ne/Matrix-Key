package com.matrixkey.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.core.graphics.ColorUtils
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Imports Gboard / Rboard ".pack" theme files (zip archives with a
 * metadata.json, one or more style_sheet*.css files and images).
 *
 * Gboard's stylesheets target Gboard's own layout, so this reads the colors
 * (and a background image when there is one) and maps them onto Matrix Key's
 * palette. Anything it can't interpret falls back to sensible defaults.
 */
object RboardImporter {

    class ImportException(message: String) : Exception(message)

    fun import(context: Context, uri: Uri): KeyboardTheme {
        val css = StringBuilder()
        var meta: JSONObject? = null
        var bgImage: ByteArray? = null
        var bgImageScore = -1

        val input = context.contentResolver.openInputStream(uri) ?: throw ImportException("Couldn't open the file")
        ZipInputStream(input.buffered()).use { zip ->
            var entries = 0
            var total = 0L
            // Reads at most [limit] bytes of the current entry; refuses oversized themes.
            fun read(limit: Int): ByteArray {
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = zip.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    total += n
                    if (out.size() > limit || total > 40L * 1024 * 1024) throw ImportException("This theme file is too large")
                }
                return out.toByteArray()
            }
            while (true) {
                val entry = zip.nextEntry ?: break
                if (++entries > 2000) throw ImportException("This theme file has too many parts")
                if (entry.isDirectory) continue
                val name = entry.name.lowercase()
                when {
                    name.endsWith(".css") -> css.append(read(2 * 1024 * 1024).toString(Charsets.UTF_8)).append('\n')
                    name.endsWith("metadata.json") -> meta = runCatching { JSONObject(read(256 * 1024).toString(Charsets.UTF_8)) }.getOrNull()
                    name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".webp") -> {
                        val bytes = read(12 * 1024 * 1024)
                        val score = imageScore(name, bytes)
                        if (score > bgImageScore) {
                            bgImageScore = score
                            bgImage = bytes
                        }
                    }
                }
            }
        }
        if (css.isEmpty()) throw ImportException("This doesn't look like a Gboard/Rboard theme (no stylesheet found)")

        val defs = parseDefs(css.toString())
        val allColors = Regex("#[0-9a-fA-F]{3,8}\\b|rgba?\\([^)]*\\)").findAll(css).mapNotNull { parseColor(it.value) }.toList()

        fun pick(vararg wanted: List<String>, exclude: List<String> = emptyList()): Int? {
            for (words in wanted) {
                val hit = defs.entries.firstOrNull { (k, _) ->
                    words.all { k.contains(it) } && exclude.none { k.contains(it) }
                }
                if (hit != null) return hit.value
            }
            return null
        }

        val mostCommon = allColors.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }

        val bg = pick(listOf("keyboard", "bg"), listOf("bg"), listOf("background"), exclude = listOf("key", "press", "popup"))
            ?: mostCommon.getOrNull(0) ?: Color.BLACK
        val key = pick(listOf("key", "bg"), listOf("key", "background"), exclude = listOf("function", "press", "action", "enter"))
            ?: mostCommon.firstOrNull { it != bg } ?: shift(bg, 0.08f)
        val mod = pick(listOf("function", "bg"), listOf("function", "key"), listOf("mod", "key"), exclude = listOf("press"))
            ?: shift(key, -0.04f)
        val text = pick(listOf("label"), listOf("text"), listOf("font"), listOf("letter"), exclude = listOf("hint", "secondary"))
            ?: if (ColorUtils.calculateLuminance(bg) < 0.5) Color.WHITE else Color.BLACK
        val accent = pick(listOf("accent"), listOf("action"), listOf("enter"), listOf("highlight"))
            ?: mostCommon.firstOrNull { saturation(it) > 0.35f } ?: Color.parseColor("#D71921")
        val trail = pick(listOf("gesture"), listOf("trail"), listOf("swipe"))

        val isDark = ColorUtils.calculateLuminance(bg) < 0.45
        val id = "custom:" + System.currentTimeMillis().toString(36)
        val name = meta?.optString("name")?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast('/')?.substringBeforeLast('.')?.take(24)
            ?: "Imported"

        var bgPath: String? = null
        bgImage?.let { bytes ->
            if (bgImageScore >= 2) {
                val f = File(ThemeManager.themesDir(context), id.removePrefix("custom:") + "_bg.img")
                f.writeBytes(bytes)
                bgPath = f.absolutePath
            }
        }

        val theme = KeyboardTheme(
            id = id, name = name, bg = bg, key = key, mod = mod,
            pill = shift(key, if (isDark) 0.06f else -0.06f), chip = key,
            text = text, sub = ColorUtils.blendARGB(text, bg, 0.42f),
            divider = ColorUtils.blendARGB(text, bg, 0.7f),
            accent = accent,
            onAccent = if (ColorUtils.calculateLuminance(accent) < 0.5) Color.WHITE else Color.BLACK,
            isDark = isDark, trail = trail, backgroundImage = bgPath,
        )
        ThemeManager.saveCustom(context, theme)
        return theme
    }

    /** `@def some_name #RRGGBB;` style declarations, keyed by lower-case name. */
    private fun parseDefs(css: String): LinkedHashMap<String, Int> {
        val out = LinkedHashMap<String, Int>()
        Regex("@def\\s+([A-Za-z0-9_\\-]+)\\s+([^;]+);").findAll(css).forEach { m ->
            parseColor(m.groupValues[2].trim())?.let { out[m.groupValues[1].lowercase()] = it }
        }
        // Also accept plain CSS custom properties: --name: #color;
        Regex("--([A-Za-z0-9_\\-]+)\\s*:\\s*([^;]+);").findAll(css).forEach { m ->
            parseColor(m.groupValues[2].trim())?.let { out.putIfAbsent(m.groupValues[1].lowercase(), it) }
        }
        return out
    }

    private fun parseColor(raw: String): Int? {
        val v = raw.trim()
        if (v.startsWith("#")) {
            val hex = v.substring(1)
            return runCatching {
                when (hex.length) {
                    3 -> Color.parseColor("#" + hex.map { "$it$it" }.joinToString(""))
                    4 -> { // #RGBA
                        val e = hex.map { "$it$it" }
                        Color.parseColor("#" + e[3] + e[0] + e[1] + e[2])
                    }
                    6 -> Color.parseColor("#$hex")
                    8 -> { // CSS order is RRGGBBAA
                        Color.parseColor("#" + hex.substring(6, 8) + hex.substring(0, 6))
                    }
                    else -> null
                }
            }.getOrNull()
        }
        val m = Regex("rgba?\\(([^)]*)\\)").find(v) ?: return null
        val parts = m.groupValues[1].split(",").map { it.trim() }
        if (parts.size < 3) return null
        val r = parts[0].toFloatOrNull()?.toInt() ?: return null
        val g = parts[1].toFloatOrNull()?.toInt() ?: return null
        val b = parts[2].toFloatOrNull()?.toInt() ?: return null
        val a = parts.getOrNull(3)?.toFloatOrNull()?.let { (it * 255).toInt() } ?: 255
        return Color.argb(a.coerceIn(0, 255), r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    private fun imageScore(name: String, bytes: ByteArray): Int {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        var score = 0
        if (opts.outWidth >= 400 && opts.outHeight >= 150) score += 2
        if (name.contains("background") || name.contains("bg") || name.contains("wallpaper")) score += 2
        if (name.contains("key") || name.contains("icon") || name.contains("preview")) score -= 2
        return score
    }

    private fun shift(color: Int, amount: Float): Int =
        if (amount >= 0) ColorUtils.blendARGB(color, Color.WHITE, amount)
        else ColorUtils.blendARGB(color, Color.BLACK, -amount)

    private fun saturation(color: Int): Float {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        return hsl[1]
    }
}
