package com.matrixkey.engine

import android.content.Context
import android.graphics.PointF
import org.json.JSONObject
import java.io.File
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Suggestions, autocorrect, next-word prediction, glide-typing decoding and
 * on-device learning. Everything here stays on the phone.
 */
class WordEngine(private val context: Context) {

    /** Lower-case word → base frequency score (log scale, higher is more common). */
    private val base = HashMap<String, Float>(60000)
    /** Words grouped by length for fast correction lookups. */
    private val byLength = HashMap<Int, MutableList<String>>()
    /** Sorted word list for prefix completion. */
    private var sorted: Array<String> = emptyArray()
    /** Words grouped by first+last letter, for glide decoding. */
    private val byEnds = HashMap<String, MutableList<String>>()

    // ---- Learned data ----
    private val userCounts = HashMap<String, Int>()          // words you type
    private val personal = sortedSetOf<String>()              // words you added
    private val bigrams = HashMap<String, HashMap<String, Int>>() // previous word → next word counts
    private val habits = HashMap<String, String>()            // your typo → the word you meant
    private val blocked = HashSet<String>()                   // words you removed
    /** Display casing for words learned with capitals (names etc.). */
    private val casing = HashMap<String, String>()

    @Volatile var ready = false
        private set

    private val dataFile get() = File(context.filesDir, "learned.json")
    private var dirty = false

    // Set by the keyboard view after layout: letter → key center, plus key size.
    var keyCenters: Map<Char, PointF> = emptyMap()
    var keyWidth = 1f

    fun load() {
        runCatching {
            context.assets.open("dict/en_50k.txt").bufferedReader().useLines { lines ->
                var rank = 0
                lines.forEach { line ->
                    val sp = line.indexOf(' ')
                    val w = (if (sp > 0) line.substring(0, sp) else line).trim().lowercase()
                    val count = if (sp > 0) line.substring(sp + 1).trim().toLongOrNull() ?: 1L else 1L
                    if (w.isEmpty() || !w.all { it.isLetter() || it == '\'' }) return@forEach
                    if (w.startsWith('\'') || w.endsWith('\'')) return@forEach
                    if (w.length == 1 && w != "a" && w != "i") return@forEach
                    if (base.containsKey(w)) return@forEach
                    base[w] = ln(count.toFloat() + 1f)
                    rank++
                }
            }
        }
        if (base.isEmpty()) FALLBACK.forEachIndexed { i, w -> base[w] = 20f - i * 0.01f }
        loadLearned()
        rebuildIndexes()
        ready = true
    }

    /** "dont" → "don't" for every contraction in the word list. */
    private val contractions = HashMap<String, String>()

    private fun rebuildIndexes() {
        byLength.clear(); byEnds.clear(); contractions.clear()
        for ((w, f) in base) {
            if ('\'' !in w) continue
            val plain = w.replace("'", "")
            val cur = contractions[plain]
            if (cur == null || (base[cur] ?: 0f) < f) contractions[plain] = w
        }
        listOf("i'm", "i've", "i'll", "i'd").forEach { casing[it] = it.replaceFirstChar { c -> c.uppercase() } }
        casing["i"] = "I"
        val all = HashSet<String>(base.keys).apply { addAll(personal); addAll(userCounts.filterValues { it >= LEARN_AFTER }.keys) }
        all.removeAll(blocked)
        for (w in all) {
            byLength.getOrPut(w.length) { ArrayList() }.add(w)
            val letters = w.filter { it.isLetter() }
            if (letters.isNotEmpty()) byEnds.getOrPut("${letters.first()}${letters.last()}") { ArrayList() }.add(w)
        }
        sorted = all.toTypedArray().also { it.sort() }
    }

    // ------------------------------------------------------------------
    // Scoring
    // ------------------------------------------------------------------

    fun isKnown(word: String): Boolean {
        val w = word.lowercase()
        if (w in blocked) return false
        return base.containsKey(w) || w in personal || (userCounts[w] ?: 0) >= LEARN_AFTER
    }

    private fun freq(w: String): Float {
        val b = base[w] ?: 0f
        val u = userCounts[w] ?: 0
        val p = if (w in personal) 12f else 0f
        return b + p + ln(1f + u) * 2.2f
    }

    private fun bigramBoost(prev: String?, w: String): Float {
        if (prev == null) return 0f
        val n = bigrams[prev]?.get(w) ?: return 0f
        return ln(1f + n) * 3f
    }

    fun display(w: String): String = casing[w] ?: w

    // ------------------------------------------------------------------
    // Typing suggestions & autocorrect
    // ------------------------------------------------------------------

    data class Result(val suggestions: List<String>, val autoCorrection: String?)

    /**
     * Suggestions for the word being typed.
     * [typed] keeps the person's capitalization; results are re-cased to match.
     */
    fun suggest(typed: String, prev: String?, blockOffensive: Boolean, allowAutocorrect: Boolean): Result {
        if (!ready || typed.isEmpty()) return Result(emptyList(), null)
        val lower = typed.lowercase()
        val candidates = HashMap<String, Float>()

        // 1. Habits: a typo you've corrected before.
        habits[lower]?.let { candidates[it] = 1000f }

        // 2. Completions.
        val idx = lowerBound(lower)
        var i = idx
        var seen = 0
        while (i < sorted.size && sorted[i].startsWith(lower) && seen < 400) {
            val w = sorted[i]
            if (w != lower) candidates[w] = max(candidates[w] ?: -1e9f, freq(w) + bigramBoost(prev, w) - (w.length - lower.length) * 0.35f - 1.5f)
            i++; seen++
        }

        // 3. Corrections (keyboard-aware edit distance).
        val maxDist = when {
            lower.length <= 2 -> 0.9f
            lower.length <= 4 -> 1.3f
            lower.length <= 7 -> 2.1f
            else -> 2.9f
        }
        for (len in (lower.length - 2)..(lower.length + 2)) {
            val list = byLength[len] ?: continue
            for (w in list) {
                if (w == lower) continue
                val d = editDistance(lower, w, maxDist)
                if (d <= maxDist) {
                    val s = freq(w) + bigramBoost(prev, w) - d * 5.5f
                    if (s > (candidates[w] ?: -1e9f)) candidates[w] = s
                }
            }
        }

        // "dont" → "don't", "im" → "I'm"
        val typedKnownEarly = isKnown(lower)
        val contraction = contractions[lower]?.takeIf { !typedKnownEarly || lower in CONTRACTION_TYPOS }
            ?.takeIf { (userCounts[lower] ?: 0) == 0 }
        if (contraction != null) candidates[contraction] = (candidates.values.maxOrNull() ?: 0f) + 1f

        val filtered = candidates.entries
            .filter { !blockOffensive || it.key !in OFFENSIVE || it.key == lower }
            .sortedByDescending { it.value }
            .map { it.key }

        val typedKnown = isKnown(lower)
        val typedScore = if (typedKnown) freq(lower) + bigramBoost(prev, lower) else -1e9f

        // Autocorrect only when the typed word is unknown (or a learned typo),
        // and the best candidate is clearly a correction, not just a longer completion.
        var auto: String? = null
        if (contraction != null && allowAutocorrect) auto = contraction
        // "i" → "I"
        if (auto == null && allowAutocorrect && lower in I_WORDS && typed == lower) auto = lower
        if (auto == null && allowAutocorrect && lower.length >= 2) {
            val top = filtered.firstOrNull()
            if (top != null) {
                val topScore = candidates[top] ?: 0f
                val isHabit = habits[lower] == top
                val dist = editDistance(lower, top, maxDist)
                val isCorrection = dist <= maxDist && !top.startsWith(lower)
                // The word list contains common misspellings ("tommorow"), so a
                // "known" word that's far rarer than a close match still gets fixed,
                // unless you've typed it yourself before.
                val rareMisspelling = typedKnown && isCorrection && lower.length > 3 && top.length >= lower.length &&
                    dist <= 1.6f && (userCounts[lower] ?: 0) == 0 && lower !in personal &&
                    freq(top) - freq(lower) > 5f
                if (isHabit || (!typedKnown && (isCorrection || (top.startsWith(lower) && top.length - lower.length <= 1))) ||
                    (typedKnown && isCorrection && topScore - typedScore > 9f && lower.length > 3) || rareMisspelling
                ) auto = top
            }
        }

        val list = ArrayList<String>()
        if (auto != null) list += auto
        filtered.forEach { if (list.size < 6 && it !in list) list += it }
        return Result(list.map { matchCase(typed, display(it)) }, auto?.let { matchCase(typed, display(it)) })
    }

    /** Next-word predictions after a completed word. */
    fun predict(prev: String?, blockOffensive: Boolean): List<String> {
        if (!ready) return emptyList()
        val p = prev?.lowercase()
        val fromBigram = p?.let { bigrams[it] }?.entries?.sortedByDescending { it.value }?.map { it.key } ?: emptyList()
        val fromCommon = p?.let { COMMON_NEXT[it] } ?: emptyList()
        val out = LinkedHashSet<String>()
        (fromBigram + fromCommon + DEFAULT_NEXT).forEach { w ->
            if (out.size < 3 && (!blockOffensive || w !in OFFENSIVE) && w !in blocked) out += display(w)
        }
        return out.toList()
    }

    private fun lowerBound(prefix: String): Int {
        var lo = 0; var hi = sorted.size
        while (lo < hi) {
            val mid = (lo + hi) ushr 1
            if (sorted[mid] < prefix) lo = mid + 1 else hi = mid
        }
        return lo
    }

    /** Damerau-Levenshtein with cheaper substitutions between neighbouring keys. */
    fun editDistance(a: String, b: String, cap: Float): Float {
        val n = a.length; val m = b.length
        if (kotlin.math.abs(n - m) > cap + 0.5f) return Float.MAX_VALUE
        var prev2 = FloatArray(m + 1)
        var prev = FloatArray(m + 1) { it.toFloat() }
        var cur = FloatArray(m + 1)
        for (i in 1..n) {
            cur[0] = i.toFloat()
            var rowMin = cur[0]
            for (j in 1..m) {
                val ca = a[i - 1]; val cb = b[j - 1]
                val sub = if (ca == cb) 0f else substitutionCost(ca, cb)
                // Dropping or doubling a letter next to itself is a cheap, common slip.
                val del = if (i >= 2 && a[i - 2] == ca) 0.6f else 1f
                val ins = if (j >= 2 && b[j - 2] == cb) 0.6f else 1f
                var v = min(min(prev[j] + del, cur[j - 1] + ins), prev[j - 1] + sub)
                if (i > 1 && j > 1 && ca == b[j - 2] && a[i - 2] == cb) v = min(v, prev2[j - 2] + 0.7f)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > cap) return Float.MAX_VALUE
            val t = prev2; prev2 = prev; prev = cur; cur = t
        }
        return prev[m]
    }

    private fun substitutionCost(a: Char, b: Char): Float {
        val pa = keyCenters[a] ?: return 1f
        val pb = keyCenters[b] ?: return 1f
        val dx = (pa.x - pb.x) / keyWidth; val dy = (pa.y - pb.y) / keyWidth
        val d = sqrt(dx * dx + dy * dy)
        return when {
            d < 1.25f -> 0.45f // neighbouring keys
            d < 2.1f -> 0.8f
            else -> 1f
        }
    }

    // ------------------------------------------------------------------
    // Glide typing
    // ------------------------------------------------------------------

    /** Decodes a swipe path into ranked words. Path points are in keyboard coordinates. */
    fun decodeGlide(path: List<PointF>, prev: String?, blockOffensive: Boolean): List<String> {
        if (!ready || path.size < 2 || keyCenters.isEmpty()) return emptyList()
        val n = 40
        val user = resample(path, n)
        val radius = keyWidth * 1.6f
        val startKeys = keyCenters.filter { dist(it.value, path.first()) < radius }.keys
        val endKeys = keyCenters.filter { dist(it.value, path.last()) < radius }.keys
        val pathLen = length(path)

        val scored = ArrayList<Pair<String, Float>>()
        for (s in startKeys) for (e in endKeys) {
            val list = byEnds["$s$e"] ?: continue
            for (w in list) {
                if (w.length < 2) continue
                val pts = idealPath(w) ?: continue
                val idealLen = length(pts)
                // Quick length check before the full comparison.
                if (idealLen > pathLen * 2.2f + keyWidth * 2 || idealLen < pathLen * 0.35f - keyWidth) continue
                val ideal = if (pts.size == 1) List(n) { pts[0] } else resample(pts, n)
                var sum = 0f
                for (k in 0 until n) sum += dist(user[k], ideal[k])
                val shape = sum / n / keyWidth
                // Every letter should be passed reasonably close to.
                val miss = coverage(path, pts)
                val score = -shape * 9f - miss * 6f + freq(w) * 0.9f + bigramBoost(prev, w) +
                    (if (habitsGlide[w] != null) 4f else 0f)
                scored += w to score
            }
        }
        return scored.asSequence()
            .filter { !blockOffensive || it.first !in OFFENSIVE }
            .sortedByDescending { it.second }
            .map { display(it.first) }
            .distinct()
            .take(5)
            .toList()
    }

    private val habitsGlide = HashMap<String, Int>()

    private fun idealPath(w: String): List<PointF>? {
        val out = ArrayList<PointF>()
        var last = ' '
        for (c in w) {
            if (c == last) continue
            val p = keyCenters[c] ?: if (c == '\'') continue else return null
            out += p
            last = c
        }
        return out
    }

    private fun coverage(path: List<PointF>, keys: List<PointF>): Float {
        var total = 0f
        for (k in keys) {
            var best = Float.MAX_VALUE
            for (p in path) {
                val d = dist(p, k)
                if (d < best) best = d
            }
            total += max(0f, best / keyWidth - 0.55f)
        }
        return total
    }

    private fun dist(a: PointF, b: PointF): Float {
        val dx = a.x - b.x; val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun length(p: List<PointF>): Float {
        var l = 0f
        for (i in 1 until p.size) l += dist(p[i - 1], p[i])
        return l
    }

    private fun resample(src: List<PointF>, n: Int): List<PointF> {
        val total = length(src)
        if (total <= 0f) return List(n) { PointF(src[0].x, src[0].y) }
        val step = total / (n - 1)
        val out = ArrayList<PointF>(n)
        out += PointF(src[0].x, src[0].y)
        var acc = 0f
        var i = 1
        var prev = PointF(src[0].x, src[0].y)
        while (i < src.size && out.size < n) {
            val cur = src[i]
            val d = dist(prev, cur)
            if (acc + d >= step && d > 0f) {
                val t = (step - acc) / d
                val q = PointF(prev.x + t * (cur.x - prev.x), prev.y + t * (cur.y - prev.y))
                out += q
                prev = q
                acc = 0f
            } else {
                acc += d
                prev = cur
                i++
            }
        }
        while (out.size < n) out += PointF(src.last().x, src.last().y)
        return out
    }

    // ------------------------------------------------------------------
    // Learning
    // ------------------------------------------------------------------

    /** Called when a word is committed (typed, picked or glided). */
    fun learnWord(word: String, prev: String?) {
        val w = word.lowercase().trim { !it.isLetter() && it != '\'' }
        if (w.isEmpty() || w.length > 30 || !w.any { it.isLetter() }) return
        val wasKnown = isKnown(w)
        userCounts[w] = (userCounts[w] ?: 0) + 1
        if (word.isNotEmpty() && word[0].isUpperCase() && word.drop(1).all { !it.isUpperCase() } && !base.containsKey(w)) {
            casing[w] = word.trim { !it.isLetterOrDigit() && it != '\'' }
        }
        prev?.lowercase()?.let { p -> if (p.isNotEmpty()) {
            val m = bigrams.getOrPut(p) { HashMap() }
            m[w] = (m[w] ?: 0) + 1
        } }
        blocked.remove(w)
        if (!wasKnown && isKnown(w)) rebuildIndexes()
        dirty = true
    }

    /** You undid an autocorrection: stop correcting this word. */
    fun learnRejectedCorrection(typed: String) {
        val t = typed.lowercase()
        habits.remove(t)
        userCounts[t] = max(userCounts[t] ?: 0, LEARN_AFTER)
        rebuildIndexes()
        dirty = true
    }

    /** You picked [chosen] while having typed [typed]: remember the slip. */
    fun learnCorrection(typed: String, chosen: String) {
        val t = typed.lowercase(); val c = chosen.lowercase()
        if (t == c || t.length < 2) return
        if (editDistance(t, c, 3.5f) <= 3.5f) habits[t] = c
        dirty = true
    }

    fun learnGlide(word: String) {
        val w = word.lowercase()
        habitsGlide[w] = (habitsGlide[w] ?: 0) + 1
        dirty = true
    }

    // Personal dictionary management -------------------------------------

    fun personalWords(): List<String> = personal.toList()
    fun learnedWords(): List<Pair<String, Int>> =
        userCounts.filter { !base.containsKey(it.key) && it.key !in personal && it.value >= LEARN_AFTER }
            .entries.sortedByDescending { it.value }.map { display(it.key) to it.value }

    fun addPersonal(word: String) {
        val w = word.trim()
        if (w.isEmpty()) return
        personal += w.lowercase()
        if (w[0].isUpperCase()) casing[w.lowercase()] = w
        blocked.remove(w.lowercase())
        rebuildIndexes(); dirty = true; save()
    }

    fun removeWord(word: String) {
        val w = word.lowercase()
        personal.remove(w); userCounts.remove(w); casing.remove(w)
        habits.entries.removeAll { it.value == w }
        blocked += w
        rebuildIndexes(); dirty = true; save()
    }

    fun resetLearned() {
        userCounts.clear(); bigrams.clear(); habits.clear(); habitsGlide.clear(); blocked.clear(); casing.clear()
        dirty = true; save(); rebuildIndexes()
    }

    fun exportJson(): String = toJson().toString(2)

    fun importJson(text: String) {
        val o = JSONObject(text)
        fromJson(o, merge = true)
        rebuildIndexes(); dirty = true; save()
    }

    // Persistence --------------------------------------------------------

    private fun toJson() = JSONObject().apply {
        put("user", JSONObject(userCounts as Map<*, *>))
        put("personal", org.json.JSONArray(personal.toList()))
        put("habits", JSONObject(habits as Map<*, *>))
        put("glide", JSONObject(habitsGlide as Map<*, *>))
        put("blocked", org.json.JSONArray(blocked.toList()))
        put("casing", JSONObject(casing as Map<*, *>))
        val bg = JSONObject()
        bigrams.forEach { (k, v) -> bg.put(k, JSONObject(v as Map<*, *>)) }
        put("bigrams", bg)
    }

    private fun fromJson(o: JSONObject, merge: Boolean) {
        if (!merge) { userCounts.clear(); personal.clear(); habits.clear(); bigrams.clear(); blocked.clear(); casing.clear() }
        o.optJSONObject("user")?.let { u -> u.keys().forEach { k -> userCounts[k] = (userCounts[k] ?: 0) + u.optInt(k) } }
        o.optJSONArray("personal")?.let { a -> for (i in 0 until a.length()) personal += a.getString(i) }
        o.optJSONObject("habits")?.let { h -> h.keys().forEach { k -> habits[k] = h.getString(k) } }
        o.optJSONObject("glide")?.let { h -> h.keys().forEach { k -> habitsGlide[k] = h.optInt(k) } }
        o.optJSONArray("blocked")?.let { a -> for (i in 0 until a.length()) blocked += a.getString(i) }
        o.optJSONObject("casing")?.let { h -> h.keys().forEach { k -> casing[k] = h.getString(k) } }
        o.optJSONObject("bigrams")?.let { b ->
            b.keys().forEach { k ->
                val inner = b.getJSONObject(k)
                val m = bigrams.getOrPut(k) { HashMap() }
                inner.keys().forEach { w -> m[w] = (m[w] ?: 0) + inner.optInt(w) }
            }
        }
    }

    private fun loadLearned() {
        val f = dataFile
        if (f.exists()) runCatching { fromJson(JSONObject(f.readText()), merge = false) }
    }

    fun save() {
        if (!dirty) return
        runCatching {
            val tmp = File(dataFile.parentFile, "learned.json.tmp")
            tmp.writeText(toJson().toString())
            tmp.renameTo(dataFile)
            dirty = false
        }
    }

    companion object {
        /** A new word becomes a suggestion after you've typed it this many times. */
        const val LEARN_AFTER = 2

        private val I_WORDS = setOf("i", "i'm", "i've", "i'll", "i'd")

        /** Real words that are almost always a missing apostrophe. */
        private val CONTRACTION_TYPOS = setOf(
            "dont", "cant", "wont", "im", "ive", "thats", "whats", "youre", "theyre", "didnt", "doesnt", "isnt",
            "wasnt", "arent", "werent", "havent", "hasnt", "couldnt", "wouldnt", "shouldnt", "aint", "youve",
            "theyve", "weve", "youll", "theyll", "itll", "thatll", "wouldve", "couldve", "shouldve", "whos", "theres",
        )

        fun matchCase(typed: String, word: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.isNotEmpty() && typed[0].isUpperCase() -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }

        private val FALLBACK = listOf("the", "to", "and", "a", "i", "you", "it", "is", "of", "that", "in", "for", "on", "this", "my", "me", "with", "be", "have", "so", "but", "not", "just", "we", "are", "was", "what", "do", "can", "your", "at", "if", "all", "like", "know", "get", "up", "no", "yes", "ok", "okay", "thanks", "good", "love", "time", "will", "how", "now", "one", "about", "out", "there", "when", "go", "they", "would", "want", "think", "see", "here", "really", "right", "well", "got", "make", "going", "today", "tomorrow", "appropriate")

        private val DEFAULT_NEXT = listOf("I", "the", "and", "to", "you", "it", "is")

        private val COMMON_NEXT = mapOf(
            "i" to listOf("am", "think", "have"), "i'm" to listOf("not", "going", "so"),
            "thank" to listOf("you", "god", "goodness"), "how" to listOf("are", "is", "about"),
            "are" to listOf("you", "we", "they"), "what" to listOf("is", "are", "do"),
            "on" to listOf("the", "my", "it"), "in" to listOf("the", "a", "my"),
            "it" to listOf("is", "was", "will"), "you" to listOf("are", "can", "know"),
            "see" to listOf("you", "it", "the"), "of" to listOf("the", "my", "course"),
            "good" to listOf("morning", "night", "luck"), "let" to listOf("me", "us", "it"),
            "talk" to listOf("to", "soon", "later"), "on" to listOf("my", "the", "way"),
            "going" to listOf("to", "out", "home"), "want" to listOf("to", "it", "a"),
            "will" to listOf("be", "do", "you"), "can" to listOf("you", "i", "we"),
        )

        /** Never suggested (unless typed exactly) when "Block offensive words" is on. */
        val OFFENSIVE = setOf(
            "fuck", "fucking", "fucked", "fucker", "shit", "shitty", "bitch", "bitches", "bastard", "cunt",
            "dick", "dickhead", "cock", "pussy", "asshole", "slut", "whore", "fag", "faggot", "retard",
            "nigger", "nigga", "twat", "wanker", "motherfucker", "bullshit", "damn", "piss", "crap",
        )
    }
}
