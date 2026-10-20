package com.matrixkey

import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager

/** Typed access to every setting. Keys match the preference XML files. */
class Prefs(context: Context) {
    val sp: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    private fun b(key: String, def: Boolean) = sp.getBoolean(key, def)
    private fun s(key: String, def: String) = sp.getString(key, def) ?: def
    private fun i(key: String, def: Int) = sp.getInt(key, def)

    // Theme
    val theme get() = s("theme", "dynamic")
    val keyShape get() = s("key_shape", "none")
    val keyFont get() = s("key_font", "ndot")
    val accent get() = s("accent", "")

    // Preferences
    val layout get() = s("layout", "qwerty")
    val numberRow get() = b("number_row", true)
    val symbolHints get() = b("symbol_hints", true)
    val emojiKey get() = b("emoji_key", true)
    val toolbar get() = b("toolbar", true)
    val oneHanded get() = s("one_handed", "off")
    val heightScale get() = s("kb_height", "1.0").toFloatOrNull() ?: 1f
    val sound get() = b("sound", false)
    val soundVolume get() = i("sound_volume", 40)
    val haptic get() = b("haptic", true)
    val vibrationStrength get() = i("vibration_strength", 50)
    val popup get() = b("popup", true)
    val longPressDelay get() = s("long_press_delay", "300").toLongOrNull() ?: 300L

    // Text correction
    val showSuggestions get() = b("show_suggestions", true)
    val nextWord get() = b("next_word", true)
    val emojiSuggestions get() = b("emoji_suggestions", true)
    val blockOffensive get() = b("block_offensive", true)
    val autocorrect get() = b("autocorrect", true)
    val learn get() = b("learn", true)
    val learnAim get() = b("learn_aim", true)
    val autoCaps get() = b("auto_caps", true)
    val doubleSpacePeriod get() = b("double_space_period", true)

    // Glide
    val glide get() = b("glide", true)
    val glideTrail get() = b("glide_trail", true)
    val trailStyle get() = s("trail_style", "auto")
    val trailWidth get() = s("trail_width", "medium")
    val trailColor get() = s("trail_color", "accent")
    val gestureDelete get() = b("gesture_delete", true)
    val gestureCursor get() = b("gesture_cursor", true)
    val cursorHaptic get() = b("cursor_haptic", true)

    // Voice
    val voice get() = b("voice", true)
    val voiceEngine get() = s("voice_engine", "ondevice")
    val voicePunct get() = b("voice_punct", true)

    // AI
    val ai get() = b("ai", true)
    val aiPrimary get() = s("ai_primary", "gemini")
    val geminiKey get() = s("gemini_key", "").trim()
    val geminiModel get() = s("gemini_model", "gemini-2.5-flash").trim().ifEmpty { "gemini-2.5-flash" }
    val aiBackup get() = b("ai_backup", true)
    val groqKey get() = s("groq_key", "").trim()
    val groqModel get() = s("groq_model", "llama-3.3-70b-versatile").trim().ifEmpty { "llama-3.3-70b-versatile" }

    // GIFs
    val gif get() = b("gif", true)
    val gifProvider get() = s("gif_provider", "giphy")
    val giphyKey get() = s("giphy_key", "").trim()
    val klipyKey get() = s("klipy_key", "").trim()
    val gifRating get() = s("gif_rating", "pg-13")
    val gifLinkFallback get() = b("gif_link_fallback", true)

    // Clipboard
    val clipboard get() = b("clipboard", true)
    val clipClearMs get() = s("clip_clear", "3600000").toLongOrNull() ?: 3600000L
    val clipHideSensitive get() = b("clip_hide_sensitive", true)

    // Privacy
    val incognito get() = b("incognito", true)

    fun put(key: String, value: String) = sp.edit().putString(key, value).apply()
}
