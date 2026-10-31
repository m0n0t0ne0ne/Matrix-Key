package com.matrixkey.engine

import android.content.Context
import android.graphics.PointF
import android.os.Handler
import android.os.HandlerThread
import org.json.JSONObject
import java.io.File
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Suggestions, autocorrect, next-word prediction, glide-typing decoding and
 * on-device learning. Everything here stays on the phone.
 *
 * There is one shared engine per app process ([get]), used by both the keyboard
 * and the Dictionary screen, and it is only touched on its own thread
 * ([handler]). That way a word added in settings is instantly part of the same
 * data the keyboard types and swipes with, and nothing can overwrite it.
 */
class WordEngine private constructor(private val context: Context) {

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

    /** Loads the word list and your learned data the first time it's needed. */
    fun ensureLoaded() { if (!ready) load() }

    private fun load() {
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
        // The bundled word list splits contractions ("don" + "t"), so add them
        // back with realistic frequencies: don't, I'm, can't, you're …
        CONTRACTION_LIST.forEach { (w, count) -> if (!base.containsKey(w)) base[w] = ln(count + 1f) }
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
        // Only "I" on its own is always a capital; "i'm", "i'll" … follow the sentence
        // (capital at the start, lowercase otherwise).
        listOf("i'm", "i've", "i'll", "i'd").forEach { casing.remove(it) }
        casing["i"] = "I"
        val all = HashSet<String>(base.keys).apply { addAll(personal); addAll(userCounts.filterValues { it >= LEARN_AFTER }.keys) }
        all.removeAll(blocked)
        for (w in all) {
            byLength.getOrPut(w.length) { ArrayList() }.add(w)
            val letters = w.filter { it.isLetter() }
            if (letters.isNotEmpty()) byEnds.getOrPut("${letters.first()}${letters.last()}") { ArrayList() }.add(w)
        }
        sorted = all.toTypedArray().also { it.sort() }
        personalSnapshot = personal.toList()
    }

    @Volatile private var personalSnapshot: List<String> = emptyList()

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
        // Learned pairs are stored in lowercase; "It" at a sentence start must find "it".
        val p = prev.lowercase().trim { !it.isLetter() && it != '\'' }
        val n = bigrams[p]?.get(w) ?: return 0f
        return ln(1f + n) * 3f
    }

    fun display(w: String): String = casing[w] ?: w

    /**
     * True when "Block offensive words" should hide [w]. Words you added to your
     * personal dictionary, or have typed yourself, are always allowed.
     */
    private fun hidden(w: String, blockOffensive: Boolean): Boolean =
        blockOffensive && w in OFFENSIVE && w !in personal && (userCounts[w] ?: 0) == 0

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
        // Look at every word with this beginning (not just the first few
        // alphabetically), so common words like "and" or "this" always compete.
        while (i < sorted.size && sorted[i].startsWith(lower) && seen < 8000) {
            val w = sorted[i]
            if (w != lower) {
                // Words you added yourself (names, your email …) are offered early,
                // however long they are.
                val score = if (w in personal) freq(w) + 6f + bigramBoost(prev, w)
                else freq(w) + bigramBoost(prev, w) - (w.length - lower.length) * 0.35f - 1.5f
                candidates[w] = max(candidates[w] ?: -1e9f, score)
            }
            i++; seen++
        }

        // The typed word itself competes on equal terms (with a small bonus
        // for being exactly what was typed), so junk like "th" can't beat "the".
        if (isKnown(lower)) candidates[lower] = max(candidates[lower] ?: -1e9f, freq(lower) + bigramBoost(prev, lower) + 1f)

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

        // "ill" vs "I'll": decide from the previous word ("feel ill" / "ill call you").
        // The other spelling always stays in the strip.
        var ambiguousAuto: String? = null
        AMBIGUOUS[lower]?.let { (contr, plainAfter) ->
            val top = (candidates.values.maxOrNull() ?: 0f)
            val wantPlain = prev?.lowercase()?.trim { !it.isLetter() } in plainAfter
            if (wantPlain) {
                candidates[lower] = top + 2f
                candidates[contr] = top + 1f
            } else {
                candidates[contr] = top + 2f
                candidates[lower] = top + 1f
                if ((userCounts[lower] ?: 0) < 3) ambiguousAuto = contr
            }
        }

        val filtered = candidates.entries
            .filter { !hidden(it.key, blockOffensive) || it.key == lower }
            .sortedByDescending { it.value }
            .map { it.key }

        val typedKnown = isKnown(lower)
        val typedScore = if (typedKnown) freq(lower) + bigramBoost(prev, lower) else -1e9f

        // Autocorrect only when the typed word is unknown (or a learned typo),
        // and the best candidate is clearly a correction, not just a longer completion.
        var auto: String? = null
        if (contraction != null && allowAutocorrect) auto = contraction
        if (auto == null && allowAutocorrect && ambiguousAuto != null) auto = ambiguousAuto
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
        filtered.forEach { if (list.size < 10 && it !in list) list += it }
        return Result(list.map { matchCase(typed, display(it)) }, auto?.let { matchCase(typed, display(it)) })
    }

    /** Next-word predictions after a completed word. */
    fun predict(prev: String?, blockOffensive: Boolean): List<String> {
        if (!ready) return emptyList()
        val p = prev?.lowercase()
        val fromBigram = p?.let { bigrams[it] }?.entries?.sortedByDescending { it.value }?.map { it.key } ?: emptyList()
        val fromCommon = (p?.let { COMMON_NEXT[it] } ?: emptyList()) + (p?.let { CONTEXT_NEXT[it] } ?: emptyList())
        val out = LinkedHashSet<String>()
        (fromBigram + fromCommon + DEFAULT_NEXT).forEach { w ->
            if (out.size < 3 && !hidden(w.lowercase(), blockOffensive) && w !in blocked) out += display(w)
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

    /**
     * Decodes a swipe path into ranked words, Swype-style. Path points are in
     * keyboard coordinates; [times] are their touch times in ms.
     *
     * 1. Path analysis: the turning points and slow-downs in the swipe ("anchors")
     *    mark the letters you meant; straight, fast stretches are just travel.
     * 2. Every word that starts and ends near the swipe's ends is a candidate.
     * 3. Each candidate is scored on:
     *    - the overall shape (elastic DTW distance between the swipe and the word's
     *      key-to-key path), which tolerates sloppy aim, speed changes and cut corners;
     *    - whether the anchors line up, in order, with the word's letters, and whether
     *      the word's own sharp turns have an anchor near them;
     *    - letters the swipe never came near, and parts of the swipe the word
     *      doesn't explain;
     *    - start/end distance and overall length;
     *    - language: how common the word is, how often you use it, and how likely
     *      it is after the previous word.
     * Weights were tuned on simulated careful, fast and sloppy swipes.
     */
    fun decodeGlide(path: List<PointF>, times: List<Long>, prev: String?, blockOffensive: Boolean): List<String> {
        if (!ready || path.size < 2 || keyCenters.isEmpty()) return emptyList()
        val n = 32
        val kw = keyWidth
        val user = resample(path, n)
        val anchorPts = if (times.size == path.size) anchors(path, times, kw) else emptyList()
        val loopPts = loops(path, kw)
        val ux = path.last().x - path.first().x; val uy = path.last().y - path.first().y
        val uLen = sqrt(ux * ux + uy * uy)
        val p0 = prev?.lowercase()?.trim { !it.isLetter() && it != '\'' }
        val nextHints = p0?.let { CONTEXT_NEXT[it] }
        val radius = kw * 1.6f
        val startKeys = keyCenters.filter { dist(it.value, path.first()) < radius }.keys
        val endKeys = keyCenters.filter { dist(it.value, path.last()) < radius }.keys
        val pathLen = length(path)

        val scored = ArrayList<Pair<String, Float>>()
        val prevRow = FloatArray(n + 1)
        val curRow = FloatArray(n + 1)
        for (s in startKeys) for (e in endKeys) {
            val list = byEnds["$s$e"] ?: continue
            for (w in list) {
                if (w.length < 2) continue
                val pts = idealPath(w) ?: continue
                val idealLen = length(pts)
                if (idealLen > pathLen * 2.2f + kw * 2 || idealLen < pathLen * 0.35f - kw) continue
                val ideal = if (pts.size == 1) List(n) { pts[0] } else resample(pts, n)

                // Banded DTW.
                val band = 6
                java.util.Arrays.fill(prevRow, Float.MAX_VALUE); prevRow[0] = 0f
                for (i in 1..n) {
                    java.util.Arrays.fill(curRow, Float.MAX_VALUE)
                    val lo = max(1, i - band); val hi = min(n, i + band)
                    for (j in lo..hi) {
                        val c = dist(user[i - 1], ideal[j - 1]) / kw
                        val best = min(prevRow[j], min(prevRow[j - 1], curRow[j - 1]))
                        curRow[j] = c + best
                    }
                    System.arraycopy(curRow, 0, prevRow, 0, n + 1)
                }
                val dtw = prevRow[n] / n

                var miss = 0f
                for (k in pts) {
                    var best = Float.MAX_VALUE
                    for (p in user) { val dd = dist(p, k); if (dd < best) best = dd }
                    miss += max(0f, best / kw - 0.5f)
                }
                var extra = 0f
                for (p in user) extra += max(0f, polyDist(p, pts) / kw - 0.5f)
                extra = extra / n * 4f
                val se = (dist(path.first(), pts.first()) + dist(path.last(), pts.last())) / kw
                val lenPen = abs(ln((idealLen / kw + 0.5f) / (pathLen / kw + 0.5f)))
                var anchorCost = 0f
                var cornerCost = 0f
                if (anchorPts.isNotEmpty()) {
                    anchorCost = anchorAlignment(anchorPts, pts, kw)
                    for (t in turnLetters(pts)) {
                        var best = Float.MAX_VALUE
                        for (a in anchorPts) { val dd = dist(t, a); if (dd < best) best = dd }
                        cornerCost += max(0f, best / kw - 0.6f)
                    }
                }
                val rank = nextHints?.indexOf(w) ?: -1
                val context = if (rank >= 0) max(0.8f, 2.5f - rank * 0.12f) else 0f

                // Two-key words are just a straight line: the ends and the direction decide.
                val short = pts.size <= 2
                var direction = 0f
                if (short && uLen > 1e-3f) {
                    val ix = pts.last().x - pts.first().x; val iy = pts.last().y - pts.first().y
                    val iLen = sqrt(ix * ix + iy * iy)
                    if (iLen > 1e-3f) direction = 1f - (ux * ix + uy * iy) / (uLen * iLen)
                }
                // Swype's double-letter gesture: a small loop on the letter ("off", "too", "feel").
                var loopFit = 0f; var loopsUnexplained = 0f; var loopsMissing = 0f
                val doubles = doubledLetters(w)
                if (loopPts.isNotEmpty() || doubles.isNotEmpty()) {
                    for (c in doubles) {
                        val kc = keyCenters[c] ?: continue
                        if (loopPts.any { dist(it, kc) < 0.85f * kw }) loopFit += 1f else if (loopPts.isNotEmpty()) loopsMissing += 1f
                    }
                    for (l in loopPts) if (doubles.none { c -> keyCenters[c]?.let { dist(l, it) < 0.85f * kw } == true }) loopsUnexplained += 1f
                }

                val score = -dtw * 6.75f - miss * 2.81f - extra * 0.28f - se * 2.475f - (if (short) se * 0.06f else 0f) -
                    lenPen * 1.69f - anchorCost * 4.45f - cornerCost * 0.7f - direction * 0.5f +
                    loopFit * 4f - loopsUnexplained * 2f - loopsMissing * 0.5f +
                    freq(w) * 0.9f + bigramBoost(prev, w) * 1.2f + context +
                    (if (habitsGlide[w] != null) 4.5f else 0f) - min(3f, (glideRejected[w] ?: 0) * 1f) +
                    ambiguityBias(w, prev) + (if (w in personal) PERSONAL_GLIDE_BONUS else 0f) -
                    (if (p0 in NO_SUBJECT_AFTER && w in SUBJECT_CONTRACTIONS) 3f else 0f)
                scored += w to score
            }
        }
        val ranked = scored.filter { !hidden(it.first, blockOffensive) }.sortedByDescending { it.second }
        val out = ranked.asSequence().map { display(it.first) }.distinct().take(5).toMutableList()
        // Words that trace exactly the same keys ("of"/"off", "to"/"too") can only be told
        // apart by a loop, so the twin of the top word is always offered next to it.
        ranked.firstOrNull()?.first?.let { best ->
            val keys = keySequence(best)
            val twin = ranked.firstOrNull { it.first != best && keySequence(it.first) == keys }
            if (twin != null) {
                val d = display(twin.first)
                if (out.indexOf(d) !in 0..1) { out.remove(d); out.add(minOf(1, out.size), d) }
            }
        }
        // A dictionary word that was a close call always makes the bar, so it's one tap away.
        val top = ranked.firstOrNull()?.second
        val mine = ranked.firstOrNull { it.first in personal }
        if (top != null && mine != null && top - mine.second < 6f) {
            val d = display(mine.first)
            if (out.indexOf(d) !in 0..2) { out.remove(d); out.add(minOf(1, out.size), d) }
        }
        return out
    }

    /**
     * Anchors: points where the finger turned sharply or slowed right down,
     * which is where people pass over the letters they mean.
     */
    private fun anchors(path: List<PointF>, times: List<Long>, kw: Float): List<PointF> {
        val n = path.size
        if (n < 5) return emptyList()
        val speed = FloatArray(n)
        for (i in 0 until n) {
            val a = max(0, i - 2); val b = min(n - 1, i + 2)
            val dt = max(1L, times[b] - times[a]).toFloat()
            var len = 0f
            for (k in a + 1..b) len += dist(path[k - 1], path[k])
            speed[i] = len / kw / dt * 100f
        }
        val median = speed.sorted()[n / 2].coerceAtLeast(1e-6f)
        val reach = 0.35f * kw
        fun back(i: Int): Int { var j = i; var acc = 0f; while (j > 0 && acc < reach) { acc += dist(path[j], path[j - 1]); j-- }; return j }
        fun fwd(i: Int): Int { var j = i; var acc = 0f; while (j < n - 1 && acc < reach) { acc += dist(path[j], path[j + 1]); j++ }; return j }
        val k = FloatArray(n)
        for (i in 1 until n - 1) {
            val a = back(i); val b = fwd(i)
            if (a == i || b == i) continue
            val v1x = path[i].x - path[a].x; val v1y = path[i].y - path[a].y
            val v2x = path[b].x - path[i].x; val v2y = path[b].y - path[i].y
            val l1 = sqrt(v1x * v1x + v1y * v1y); val l2 = sqrt(v2x * v2x + v2y * v2y)
            if (l1 < 1e-3f || l2 < 1e-3f) continue
            val cos = ((v1x * v2x + v1y * v2y) / (l1 * l2)).coerceIn(-1f, 1f)
            val turn = (kotlin.math.acos(cos) / Math.PI).toFloat()
            val slow = max(0f, 1f - speed[i] / (0.45f * median))
            k[i] = max(turn * 1.6f, slow)
        }
        val picked = ArrayList<Int>()
        for (i in 2 until n - 2) {
            var isMax = k[i] >= 0.5f
            if (isMax) for (j in max(0, i - 3)..min(n - 1, i + 3)) if (k[j] > k[i]) { isMax = false; break }
            if (!isMax) continue
            if (picked.isEmpty() || dist(path[picked.last()], path[i]) > 0.5f * kw) picked += i
            else if (k[i] > k[picked.last()]) picked[picked.size - 1] = i
        }
        return picked.map { path[it] }.filter { dist(it, path.first()) > 0.5f * kw && dist(it, path.last()) > 0.5f * kw }
    }

    /** Lines the anchors up with the word's letters, in order (letters may be skipped). */
    private fun anchorAlignment(anchorPts: List<PointF>, letters: List<PointF>, kw: Float): Float {
        val l = letters.size
        var prev = FloatArray(l)
        var cur = FloatArray(l)
        for (a in anchorPts) {
            var best = Float.MAX_VALUE
            for (j in 0 until l) {
                best = min(best, prev[j])
                cur[j] = best + max(0f, dist(a, letters[j]) / kw - 0.45f)
            }
            for (j in 1 until l) cur[j] = min(cur[j], cur[j - 1])
            val t = prev; prev = cur; cur = t
        }
        return prev[l - 1]
    }

    /** Letters where the word's own path turns sharply (more than ~70°). */
    private fun turnLetters(pts: List<PointF>): List<PointF> {
        val out = ArrayList<PointF>()
        for (i in 1 until pts.size - 1) {
            val v1x = pts[i].x - pts[i - 1].x; val v1y = pts[i].y - pts[i - 1].y
            val v2x = pts[i + 1].x - pts[i].x; val v2y = pts[i + 1].y - pts[i].y
            val l1 = sqrt(v1x * v1x + v1y * v1y); val l2 = sqrt(v2x * v2x + v2y * v2y)
            if (l1 < 1e-3f || l2 < 1e-3f) continue
            if ((v1x * v2x + v1y * v2y) / (l1 * l2) < 0.35f) out += pts[i]
        }
        return out
    }

    /** Swipes for "ill"/"I'll" trace the same keys: let the previous word decide. */
    private fun ambiguityBias(w: String, prev: String?): Float {
        // Only ever pushes one of the pair down; it must not lift "I'd" above
        // unrelated words like "of" or "is".
        val p = prev?.lowercase()?.trim { !it.isLetter() }
        for ((plain, pair) in AMBIGUOUS) {
            val (contr, plainAfter) = pair
            if (w != plain && w != contr) continue
            val wantPlain = p in plainAfter
            return if ((w == plain) == wantPlain) 0f else -6f
        }
        return 0f
    }

    /** Letters that appear twice in a row ("off" → f). */
    private fun doubledLetters(w: String): List<Char> {
        val out = ArrayList<Char>()
        for (i in 0 until w.length - 1) if (w[i] == w[i + 1] && w[i].isLetter()) out += w[i]
        return out
    }

    /** The keys a word's swipe passes, with repeats merged ("off" → "of"). */
    private fun keySequence(w: String): String {
        val sb = StringBuilder()
        for (c in w) if (c.isLetter() && (sb.isEmpty() || sb.last() != c)) sb.append(c)
        return sb.toString()
    }

    /**
     * Small loops in the swipe: the path crosses back over itself after 0.6–2.4 keys
     * of travel, turning steadily one way through most of a circle.
     */
    private fun loops(path: List<PointF>, kw: Float): List<PointF> {
        val step = 0.1f * kw
        val total = length(path)
        if (total < 0.6f * kw) return emptyList()
        val rp = resample(path, max(8, (total / step).toInt() + 1))
        val n = rp.size
        val heading = FloatArray(n - 1) { kotlin.math.atan2(rp[it + 1].y - rp[it].y, rp[it + 1].x - rp[it].x) }
        fun turnAt(k: Int): Float {
            var dh = heading[k] - heading[k - 1]
            while (dh > Math.PI) dh -= (2 * Math.PI).toFloat()
            while (dh < -Math.PI) dh += (2 * Math.PI).toFloat()
            return dh
        }
        val out = ArrayList<PointF>()
        val maxSpan = (2.4f * kw / step).toInt()
        var i = 0
        while (i < n - 6) {
            var j = i + 6
            var found = -1
            while (j < min(n, i + maxSpan)) {
                if (dist(rp[i], rp[j]) < 0.2f * kw) {
                    var turn = 0f; var turnAbs = 0f; var maxStep = 0f
                    for (k in i + 1 until min(j, n - 1)) {
                        val dh = turnAt(k)
                        turn += dh; turnAbs += abs(dh); maxStep = max(maxStep, abs(dh))
                    }
                    if (abs(turn) > 1.4f * Math.PI && abs(turn) > 0.8f * turnAbs && maxStep < 0.5f * Math.PI) {
                        var cx = 0f; var cy = 0f
                        for (k in i..j) { cx += rp[k].x; cy += rp[k].y }
                        val c = PointF(cx / (j - i + 1), cy / (j - i + 1))
                        if (out.isEmpty() || dist(out.last(), c) > 0.6f * kw) out += c
                        found = j
                    }
                    break
                }
                j++
            }
            i = if (found >= 0) found + 1 else i + 1
        }
        return out
    }

    /** Distance from a point to a polyline. */
    private fun polyDist(p: PointF, pts: List<PointF>): Float {
        if (pts.size == 1) return dist(p, pts[0])
        var best = Float.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val a = pts[i]; val b = pts[i + 1]
            val dx = b.x - a.x; val dy = b.y - a.y
            val l2 = dx * dx + dy * dy
            val t = if (l2 == 0f) 0f else (((p.x - a.x) * dx + (p.y - a.y) * dy) / l2).coerceIn(0f, 1f)
            val qx = a.x + t * dx; val qy = a.y + t * dy
            val dd = sqrt((p.x - qx) * (p.x - qx) + (p.y - qy) * (p.y - qy))
            if (dd < best) best = dd
        }
        return best
    }

    private val habitsGlide = HashMap<String, Int>()
    private val glideRejected = HashMap<String, Int>()

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

    /** Takes back what [learnWord] counted for a swiped word you then swapped for another. */
    fun unlearnWord(word: String, prev: String?) {
        val w = word.lowercase().trim { !it.isLetter() && it != '\'' }
        if (w.isEmpty()) return
        userCounts[w]?.let { if (it <= 1) userCounts.remove(w) else userCounts[w] = it - 1 }
        prev?.lowercase()?.trim { !it.isLetter() && it != '\'' }?.let { p ->
            bigrams[p]?.let { m ->
                m[w]?.let { if (it <= 1) m.remove(w) else m[w] = it - 1 }
                if (m.isEmpty()) bigrams.remove(p)
            }
        }
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

    /** You swapped a swiped word for another: rank it a little lower next time. */
    fun rejectGlide(word: String) {
        val w = word.lowercase()
        glideRejected[w] = (glideRejected[w] ?: 0) + 1
        dirty = true
    }

    // Personal dictionary management -------------------------------------

    fun personalWords(): List<String> = personal.toList()

    /** Personal-dictionary entries that start with [prefix] (for email/URL fields and the like). */
    fun personalMatches(prefix: String): List<String> {
        val p = prefix.lowercase()
        if (p.isEmpty()) return emptyList()
        // Called from the keyboard's main thread too, so read an immutable copy.
        return personalSnapshot.filter { it.startsWith(p) && it != p }.sortedBy { it.length }.map { display(it) }.take(6)
    }
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
        userCounts.clear(); bigrams.clear(); habits.clear(); habitsGlide.clear(); glideRejected.clear(); blocked.clear(); casing.clear()
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
        put("glideNo", JSONObject(glideRejected as Map<*, *>))
        put("blocked", org.json.JSONArray(blocked.toList()))
        put("casing", JSONObject(casing as Map<*, *>))
        val bg = JSONObject()
        bigrams.forEach { (k, v) -> bg.put(k, JSONObject(v as Map<*, *>)) }
        put("bigrams", bg)
    }

    private fun fromJson(o: JSONObject, merge: Boolean) {
        if (!merge) { userCounts.clear(); personal.clear(); habits.clear(); habitsGlide.clear(); glideRejected.clear(); bigrams.clear(); blocked.clear(); casing.clear() }
        o.optJSONObject("user")?.let { u -> u.keys().forEach { k -> userCounts[k] = (userCounts[k] ?: 0) + u.optInt(k) } }
        o.optJSONArray("personal")?.let { a -> for (i in 0 until a.length()) personal += a.getString(i) }
        o.optJSONObject("habits")?.let { h -> h.keys().forEach { k -> habits[k] = h.getString(k) } }
        o.optJSONObject("glide")?.let { h -> h.keys().forEach { k -> habitsGlide[k] = h.optInt(k) } }
        o.optJSONObject("glideNo")?.let { h -> h.keys().forEach { k -> glideRejected[k] = h.optInt(k) } }
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

    /** Forgets what was learned from typing; optionally keeps your personal dictionary. */
    fun resetLearned(keepPersonal: Boolean) {
        val keep = if (keepPersonal) personal.toList() else emptyList()
        val keepCasing = casing.filterKeys { it in keep }
        fromJson(JSONObject(), merge = false)
        personal += keep
        casing += keepCasing
        rebuildIndexes(); dirty = true; save()
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
        @Volatile private var shared: WordEngine? = null

        fun get(context: Context): WordEngine =
            shared ?: synchronized(this) { shared ?: WordEngine(context.applicationContext).also { shared = it } }

        private val thread by lazy { HandlerThread("words").apply { start() } }
        /** The only thread that reads or changes engine data. */
        val handler by lazy { Handler(thread.looper) }

        /** Extra glide score for personal-dictionary words (tested: no loss on common words). */
        private const val PERSONAL_GLIDE_BONUS = 2.5f

        /** A new word becomes a suggestion after you've typed it this many times. */
        const val LEARN_AFTER = 2

        /** Common English contractions with approximate usage counts (same scale as the word list). */
        private val CONTRACTION_LIST: List<Pair<String, Float>> = buildList {
            fun group(count: Float, vararg ws: String) = ws.forEach { add(it to count) }
            group(4_000_000f, "don't", "i'm", "it's", "you're", "that's", "i'll", "can't")
            group(1_500_000f, "didn't", "i've", "what's", "he's", "we're", "let's", "there's", "won't", "doesn't", "isn't", "she's", "they're")
            group(700_000f, "wasn't", "couldn't", "wouldn't", "you'll", "i'd", "we'll", "haven't", "aren't", "you've", "shouldn't", "he'll", "who's")
            group(300_000f, "you'd", "they'll", "we've", "she'll", "hasn't", "weren't", "they've", "here's", "where's", "how's", "it'll", "that'll", "ain't", "o'clock", "ma'am")
            group(100_000f, "he'd", "she'd", "we'd", "they'd", "it'd", "would've", "could've", "should've", "who'll", "what'll", "hadn't", "y'all", "there'll", "that'd", "who'd", "what're", "why's", "when's", "might've", "must've", "mustn't", "needn't", "everyone's", "nobody's", "somebody's", "someone's")
        }

        private val I_WORDS = setOf("i", "i'm", "i've", "i'll", "i'd")

        /** Words that are also contractions: plain word → (contraction, previous words that mean the plain word). */
        private val AMBIGUOUS: Map<String, Pair<String, Set<String>>> = mapOf(
            "ill" to ("i'll" to setOf(
                "is", "was", "am", "are", "were", "be", "been", "being", "feel", "feels", "felt", "feeling",
                "get", "gets", "got", "getting", "gotten", "look", "looks", "looked", "looking", "seem", "seems",
                "seemed", "become", "became", "fell", "falls", "fall", "so", "very", "really", "too", "quite",
                "pretty", "kinda", "bit", "an", "the", "terminally", "critically", "mentally", "seriously",
                "chronically", "gravely", "taken", "she's", "he's", "i'm", "you're", "they're", "we're", "it's",
                "an", "of", "speak", "think", "spoke", "thought", "with", "no", "not", "never"
            )),
            "id" to ("i'd" to setOf("your", "my", "his", "her", "their", "our", "an", "the", "photo", "user", "valid", "student", "caller")),
        )

        /** Real words that are almost always a missing apostrophe. */
        private val CONTRACTION_TYPOS = setOf(
            "dont", "cant", "wont", "im", "ive", "thats", "whats", "youre", "theyre", "didnt", "doesnt", "isnt",
            "wasnt", "arent", "werent", "havent", "hasnt", "couldnt", "wouldnt", "shouldnt", "aint", "youve",
            "theyve", "weve", "youll", "theyll", "itll", "thatll", "wouldve", "couldve", "shouldve", "whos", "theres",
            "doesnt", "hasnt", "hadnt", "heres", "wheres", "hows", "whats", "theyd", "youd", "hed", "mustnt",
            "mightve", "mustve", "yall", "maam", "oclock", "hes", "shes", "itd", "thatd", "whod",
        )

        fun matchCase(typed: String, word: String): String = when {
            typed.length > 1 && typed.all { !it.isLetter() || it.isUpperCase() } -> word.uppercase()
            typed.isNotEmpty() && typed[0].isUpperCase() -> word.replaceFirstChar { it.uppercase() }
            else -> word
        }

        private val FALLBACK = listOf("the", "to", "and", "a", "i", "you", "it", "is", "of", "that", "in", "for", "on", "this", "my", "me", "with", "be", "have", "so", "but", "not", "just", "we", "are", "was", "what", "do", "can", "your", "at", "if", "all", "like", "know", "get", "up", "no", "yes", "ok", "okay", "thanks", "good", "love", "time", "will", "how", "now", "one", "about", "out", "there", "when", "go", "they", "would", "want", "think", "see", "here", "really", "right", "well", "got", "make", "going", "today", "tomorrow", "appropriate")

        /** "you're", "I'm" … start a clause; they can't follow "be", "the", "to" … ("to be upper", not "to be you're"). */
        private val SUBJECT_CONTRACTIONS = setOf(
            "i'm", "you're", "we're", "they're", "he's", "she's", "it's", "i've", "you've", "we've", "they've",
            "i'll", "you'll", "we'll", "they'll", "i'd", "you'd", "we'd", "they'd",
        )
        private val NO_SUBJECT_AFTER = setOf(
            "be", "been", "being", "to", "the", "a", "an", "my", "your", "his", "our", "their", "its",
            "of", "for", "with", "from", "am", "is", "are", "was", "were", "very",
        )

        private val DEFAULT_NEXT = listOf("I", "the", "and", "to", "you", "it", "is")

        /**
         * Words likely to follow common words ("more" → than, "over" → there …), best first.
         * Used to rank swipe candidates by context and for next-word predictions.
         */
        private val CONTEXT_NEXT: Map<String, List<String>> = run {
            val m = LinkedHashMap<String, MutableList<String>>()
            """
            i:am think have was will can don't just know love want need would really feel hope guess mean make see get got eat said did like go
            i'm:not going so sure just good fine on in at sorry
            i'll:be let see do have get call
            i've:been got never seen
            i'd:like love be say
            you:are can know have want were will should need guys too
            you're:welcome right not so the
            we:are can have were will should need could
            we're:going not all here
            they:are were have will said don't
            they're:not going all
            he:is was said has will
            she:is was said has will
            it:is was will would seems looks does has sounds works feels
            it's:a not the so ok okay just been
            that:is was i you the it's would
            that's:what why it not so a the
            this:is was one morning week weekend time
            what:is are do did time about you happened
            what's:up the going your
            how:are is about much many long was do did
            where:are is did do the
            when:you i is are the we do
            why:are is did do not would
            who:is are was knows
            can:you i we be not do get
            could:you be have i we not
            would:be you have like love not
            will:be you do have not
            should:be have we i not
            do:you not it that this
            don't:know have want think worry
            did:you it not i
            does:it not that this
            is:it the a not that this
            are:you we they there the not
            was:a the it not just so
            were:you we they not
            have:a to you been the fun
            has:been a to the
            had:a to been the
            be:a the there able home back good fine done ready sure careful
            been:a the there so
            get:the to a it back home some
            got:a it the to my
            go:to back home out
            going:to on out home
            come:back on to over here
            let:me us it you know
            let's:go do get see
            want:to a it the
            need:to a it the some
            like:a the to it that this
            love:you it the to that this
            know:what that it the how if
            think:about it so that i you
            see:you it the what if
            look:at like for forward
            make:sure it a the
            take:a care it the
            give:me it you
            tell:me you him her them
            call:me you me later
            thank:you god goodness
            thanks:for so a
            please:let help send call
            sorry:i about for to
            good:morning night luck job day
            have:a fun been
            happy:birthday to new
            on:the my it way your
            in:the a my your
            at:the home work a
            to:the be do get go see
            for:the a you me your
            with:you the me a my
            from:the my you a
            of:the my course your a
            about:the it to that you
            and:i the then you it
            but:i it the you not
            so:much i the you it good
            or:not the a
            if:you i it the we
            just:a got want wanted need
            not:sure the a to really
            very:much good well
            too:much late
            my:mom dad phone friend way
            your:mom phone way welcome
            our:own house friends
            their:own house
            there:is are was were
            here:is are
            more:than
            less:than
            better:than
            rather:than
            other:than
            fill:out in
            in:front
            right:now there
            upper:case
            lower:case
            being:so a the able
            over:there here the
            out:of there
            all:the of right
            some:of time
            one:of more day
            each:other
            see:you
            miss:you
            talk:to soon later
            on:my the
            going:to
            """.trimIndent().lines().forEach { line ->
                val k = line.substringBefore(':').trim()
                if (k.isEmpty()) return@forEach
                val list = m.getOrPut(k) { ArrayList() }
                line.substringAfter(':').trim().split(' ').filter { it.isNotBlank() && it !in list }.forEach { list += it }
            }
            m
        }

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
