package com.matrixkey.theme

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import com.matrixkey.Prefs
import org.json.JSONObject
import java.io.File

/** Every color the keyboard draws with. */
data class KeyboardTheme(
    val id: String,
    val name: String,
    val bg: Int,
    val key: Int,
    val mod: Int,
    val pill: Int,
    val chip: Int,
    val text: Int,
    val sub: Int,
    val divider: Int,
    val accent: Int,
    val onAccent: Int,
    val isDark: Boolean,
    /** Glide trail color from an imported theme, if it defines one. */
    val trail: Int? = null,
    /** Optional keyboard background image (imported Rboard themes). */
    val backgroundImage: String? = null,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("name", name); put("bg", bg); put("key", key); put("mod", mod)
        put("pill", pill); put("chip", chip); put("text", text); put("sub", sub)
        put("divider", divider); put("accent", accent); put("onAccent", onAccent)
        put("isDark", isDark)
        trail?.let { put("trail", it) }
        backgroundImage?.let { put("backgroundImage", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = KeyboardTheme(
            id = o.getString("id"), name = o.optString("name", "Imported"),
            bg = o.getInt("bg"), key = o.getInt("key"), mod = o.getInt("mod"),
            pill = o.getInt("pill"), chip = o.getInt("chip"), text = o.getInt("text"),
            sub = o.getInt("sub"), divider = o.getInt("divider"), accent = o.getInt("accent"),
            onAccent = o.getInt("onAccent"), isDark = o.optBoolean("isDark", true),
            trail = if (o.has("trail")) o.getInt("trail") else null,
            backgroundImage = if (o.has("backgroundImage")) o.getString("backgroundImage") else null,
        )
    }
}

object ThemeManager {
    private fun c(hex: String) = Color.parseColor(hex)

    val LIGHT = KeyboardTheme(
        "light", "Light", c("#E3E6E9"), c("#F5F6F8"), c("#EAECEF"), c("#D3D8DD"), c("#F5F6F8"),
        c("#111214"), c("#565B62"), c("#A3A8AE"), c("#D71921"), Color.WHITE, false
    )
    val DARK = KeyboardTheme(
        "dark", "Dark", c("#121619"), c("#1D2226"), c("#171B1F"), c("#262C31"), c("#1D2226"),
        c("#E6EAED"), c("#98A2AA"), c("#3A4248"), c("#D71921"), Color.WHITE, true
    )
    val BLACK = KeyboardTheme(
        "black", "Pure Black", c("#000000"), c("#161616"), c("#0E0E0E"), c("#1E1E1E"), c("#161616"),
        c("#F2F2F2"), c("#9A9A9A"), c("#4A4A4A"), c("#D71921"), Color.WHITE, true
    )
    val MIDNIGHT = KeyboardTheme(
        "midnight", "Midnight", c("#020A17"), c("#0B1526"), c("#07101F"), c("#13203A"), c("#0B1526"),
        c("#E8EEF7"), c("#8FA0B8"), c("#2A3A55"), c("#C62828"), Color.WHITE, true
    )

    val builtIns = listOf(BLACK, MIDNIGHT, LIGHT, DARK)

    fun isNight(context: Context): Boolean =
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    /** Resolves the theme to draw with right now, including the accent override. */
    fun resolve(context: Context, prefs: Prefs, themeId: String = prefs.theme, accent: String = prefs.accent): KeyboardTheme {
        val base = when {
            themeId == "dynamic" -> dynamic(context)
            themeId == "system" -> if (isNight(context)) DARK else LIGHT
            themeId.startsWith("custom:") -> loadCustom(context, themeId) ?: DARK
            else -> builtIns.firstOrNull { it.id == themeId } ?: DARK
        }
        val acc = accent.takeIf { it.isNotBlank() }?.let { runCatching { Color.parseColor(it) }.getOrNull() }
        return if (acc != null) base.copy(accent = acc, onAccent = Color.WHITE) else base
    }

    fun dynamic(context: Context): KeyboardTheme {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return if (isNight(context)) DARK else LIGHT
        return dynamicS(context, isNight(context))
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun dynamicS(context: Context, dark: Boolean): KeyboardTheme {
        fun g(id: Int) = context.getColor(id)
        return if (dark) KeyboardTheme(
            "dynamic", "Dynamic Color",
            bg = g(android.R.color.system_neutral1_900),
            key = g(android.R.color.system_neutral1_800),
            mod = g(android.R.color.system_neutral2_800),
            pill = g(android.R.color.system_accent2_800),
            chip = g(android.R.color.system_neutral1_800),
            text = g(android.R.color.system_neutral1_50),
            sub = g(android.R.color.system_neutral2_300),
            divider = g(android.R.color.system_neutral2_600),
            accent = g(android.R.color.system_accent1_300),
            onAccent = g(android.R.color.system_accent1_900),
            isDark = true,
        ) else KeyboardTheme(
            "dynamic", "Dynamic Color",
            bg = g(android.R.color.system_neutral1_100),
            key = g(android.R.color.system_neutral1_10),
            mod = g(android.R.color.system_neutral2_200),
            pill = g(android.R.color.system_accent2_100),
            chip = g(android.R.color.system_neutral1_10),
            text = g(android.R.color.system_neutral1_900),
            sub = g(android.R.color.system_neutral2_600),
            divider = g(android.R.color.system_neutral2_300),
            accent = g(android.R.color.system_accent1_600),
            onAccent = Color.WHITE,
            isDark = false,
        )
    }

    // ----- Imported (Rboard) themes, stored as JSON in app storage -----

    fun themesDir(context: Context) = File(context.filesDir, "themes").apply { mkdirs() }

    fun customThemes(context: Context): List<KeyboardTheme> =
        themesDir(context).listFiles { f -> f.name.endsWith(".json") }
            ?.sortedBy { it.lastModified() }
            ?.mapNotNull { f -> runCatching { KeyboardTheme.fromJson(JSONObject(f.readText())) }.getOrNull() }
            ?: emptyList()

    fun loadCustom(context: Context, themeId: String): KeyboardTheme? {
        val f = File(themesDir(context), themeId.removePrefix("custom:") + ".json")
        return if (f.exists()) runCatching { KeyboardTheme.fromJson(JSONObject(f.readText())) }.getOrNull() else null
    }

    fun saveCustom(context: Context, theme: KeyboardTheme) {
        File(themesDir(context), theme.id.removePrefix("custom:") + ".json").writeText(theme.toJson().toString())
    }

    fun deleteCustom(context: Context, themeId: String) {
        val t = loadCustom(context, themeId)
        t?.backgroundImage?.let { File(it).delete() }
        File(themesDir(context), themeId.removePrefix("custom:") + ".json").delete()
    }
}
