package com.matrixkey.keyboard

import android.graphics.RectF

enum class KeyType { CHAR, SHIFT, BACKSPACE, ENTER, SPACE, SYMBOLS, ALPHA, MORE_SYMBOLS, EMOJI, SPACER }

enum class Mode { ALPHA, SYMBOLS, MORE_SYMBOLS, NUMBER }

enum class ShiftState { OFF, ON, LOCKED }

class Key(
    val type: KeyType,
    val label: String = "",
    val weight: Float = 1f,
    /** Symbol shown in the corner and typed on long-press. */
    val hint: String? = null,
    /** Extra characters offered on long-press (accents and so on). */
    val more: List<String> = emptyList(),
    /** Function-key coloring (shift, backspace, ?123 …). */
    val mod: Boolean = false,
    /** Drawn as a pill when keys are borderless (space, ?123, enter). */
    val pill: Boolean = false,
) {
    /** Drawn area. */
    val rect = RectF()
    /** Touch area (the whole cell, gaps included). */
    val hit = RectF()
    val isLetter get() = type == KeyType.CHAR && label.length == 1 && label[0].isLetter()

    /** Everything a long-press can type, hint first. */
    val longPressOptions: List<String> get() = listOfNotNull(hint) + more
}

class Row(val keys: List<Key>, val heightDp: Float)

object Layouts {
    const val LETTER_ROW_DP = 54f
    const val NUMBER_ROW_DP = 50f

    private val letterRows = mapOf(
        "qwerty" to listOf("qwertyuiop", "asdfghjkl", "zxcvbnm"),
        "qwertz" to listOf("qwertzuiop", "asdfghjkl", "yxcvbnm"),
        "azerty" to listOf("azertyuiop", "qsdfghjklm", "wxcvbn"),
        "dvorak" to listOf("pyfgcrl", "aoeuidhtns", "qjkxbmwvz"),
        "colemak" to listOf("qwfpgjluy", "arstdhneio", "zxcvbkm"),
    )

    private val hints = mapOf(
        'q' to "%", 'w' to "\\", 'e' to "|", 'r' to "=", 't' to "[", 'y' to "]", 'u' to "<", 'i' to ">",
        'o' to "{", 'p' to "}", 'a' to "@", 's' to "#", 'd' to "$", 'f' to "_", 'g' to "&", 'h' to "-",
        'j' to "+", 'k' to "(", 'l' to ")", 'z' to "*", 'x' to "\"", 'c' to "'", 'v' to ":", 'b' to ";",
        'n' to "!", 'm' to "?",
    )

    private val accents = mapOf(
        'a' to listOf("à", "á", "â", "ä", "æ", "ã", "å", "ā"),
        'c' to listOf("ç", "ć", "č"),
        'e' to listOf("è", "é", "ê", "ë", "ē", "ę"),
        'i' to listOf("ì", "í", "î", "ï", "ī"),
        'n' to listOf("ñ", "ń"),
        'o' to listOf("ò", "ó", "ô", "ö", "õ", "ø", "œ", "ō"),
        's' to listOf("ß", "ś", "š"),
        'u' to listOf("ù", "ú", "û", "ü", "ū"),
        'y' to listOf("ý", "ÿ"),
        'z' to listOf("ž", "ź", "ż"),
    )

    private val numberHints = mapOf(
        "1" to listOf("¹", "½", "⅓", "¼"), "2" to listOf("²", "⅔"), "3" to listOf("³", "¾"),
        "0" to listOf("°", "∅"),
    )

    data class Options(
        val layout: String,
        val numberRow: Boolean,
        val emojiKey: Boolean,
    )

    fun build(mode: Mode, o: Options): List<Row> = when (mode) {
        Mode.ALPHA -> alpha(o)
        Mode.SYMBOLS -> symbols(o)
        Mode.MORE_SYMBOLS -> moreSymbols(o)
        Mode.NUMBER -> number()
    }

    private fun numberRow() = Row("1234567890".map {
        Key(KeyType.CHAR, it.toString(), more = numberHints[it.toString()] ?: emptyList())
    }, NUMBER_ROW_DP)

    private fun letterKey(c: Char) = Key(KeyType.CHAR, c.toString(), hint = hints[c], more = accents[c] ?: emptyList())

    private fun alpha(o: Options): List<Row> {
        val rows = letterRows[o.layout] ?: letterRows.getValue("qwerty")
        val widest = rows.maxOf { it.length }.coerceAtLeast(10)
        val out = mutableListOf<Row>()
        if (o.numberRow) out += numberRow()
        // Rows 1 and 2: letters, padded with half spacers when shorter.
        for (r in 0..1) {
            val letters = rows[r]
            val pad = (widest - letters.length) / 2f
            val keys = mutableListOf<Key>()
            if (pad > 0) keys += Key(KeyType.SPACER, weight = pad)
            letters.forEach { keys += letterKey(it) }
            if (pad > 0) keys += Key(KeyType.SPACER, weight = pad)
            out += Row(keys, LETTER_ROW_DP)
        }
        // Row 3: shift + letters + backspace.
        val third = rows[2]
        val side = ((widest - third.length) / 2f).coerceAtLeast(1.2f)
        out += Row(
            listOf(Key(KeyType.SHIFT, weight = side, mod = true)) +
                third.map { letterKey(it) } +
                Key(KeyType.BACKSPACE, weight = side, mod = true),
            LETTER_ROW_DP
        )
        out += bottomRow(o, symbolsLabel = "?123", alphaMode = true)
        return out
    }

    private fun bottomRow(o: Options, symbolsLabel: String, alphaMode: Boolean): Row {
        val keys = mutableListOf<Key>()
        keys += if (alphaMode) Key(KeyType.SYMBOLS, symbolsLabel, weight = 1.5f, mod = true, pill = true)
        else Key(KeyType.ALPHA, "ABC", weight = 1.5f, mod = true, pill = true)
        keys += Key(KeyType.CHAR, ",", mod = true, more = listOf(";", ":"))
        if (o.emojiKey) keys += Key(KeyType.EMOJI, mod = true)
        keys += Key(KeyType.SPACE, weight = if (o.emojiKey) 4.5f else 5.5f, pill = true)
        keys += Key(KeyType.CHAR, ".", mod = true, more = listOf("…", "?", "!", "-", "'", "\"", "/", "@"))
        keys += Key(KeyType.ENTER, weight = 1.5f, pill = true)
        return Row(keys, LETTER_ROW_DP)
    }

    private fun charRow(chars: List<String>, heightDp: Float = LETTER_ROW_DP) =
        Row(chars.map { Key(KeyType.CHAR, it) }, heightDp)

    private fun symbols(o: Options): List<Row> = listOf(
        charRow("1 2 3 4 5 6 7 8 9 0".split(" ")),
        charRow("@ # $ _ & - + ( ) /".split(" ")),
        Row(
            listOf(Key(KeyType.MORE_SYMBOLS, "=\\<", weight = 1.5f, mod = true)) +
                "* \" ' : ; ! ?".split(" ").map { Key(KeyType.CHAR, it) } +
                Key(KeyType.BACKSPACE, weight = 1.5f, mod = true),
            LETTER_ROW_DP
        ),
        bottomRow(o, "?123", alphaMode = false),
    )

    private fun moreSymbols(o: Options): List<Row> = listOf(
        charRow("~ ` | • √ π ÷ × ¶ ∆".split(" ")),
        charRow("£ ¢ € ¥ ^ ° = { } \\".split(" ")),
        Row(
            listOf(Key(KeyType.SYMBOLS, "?123", weight = 1.5f, mod = true)) +
                "% © ® ™ ✓ [ ]".split(" ").map { Key(KeyType.CHAR, it) } +
                Key(KeyType.BACKSPACE, weight = 1.5f, mod = true),
            LETTER_ROW_DP
        ),
        bottomRow(o, "?123", alphaMode = false),
    )

    private fun number(): List<Row> = listOf(
        Row(listOf("1", "2", "3", "-").map { Key(KeyType.CHAR, it, mod = it == "-") }, LETTER_ROW_DP),
        Row(listOf("4", "5", "6", "+").map { Key(KeyType.CHAR, it, mod = it == "+") }, LETTER_ROW_DP),
        Row(listOf(Key(KeyType.CHAR, "7"), Key(KeyType.CHAR, "8"), Key(KeyType.CHAR, "9"), Key(KeyType.BACKSPACE, mod = true)), LETTER_ROW_DP),
        Row(listOf(Key(KeyType.CHAR, ",", mod = true), Key(KeyType.CHAR, "0"), Key(KeyType.CHAR, ".", mod = true), Key(KeyType.ENTER, pill = true)), LETTER_ROW_DP),
    )
}
