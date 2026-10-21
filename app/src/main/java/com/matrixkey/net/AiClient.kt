package com.matrixkey.net

import com.matrixkey.Prefs
import org.json.JSONArray
import org.json.JSONObject

/**
 * AI writing through free tiers: Google Gemini first, Groq as a backup when
 * Gemini hits its rate limit (or the other way round, per settings).
 */
class AiClient(private val prefs: Prefs) {

    enum class Action(val label: String, val instruction: String) {
        PROOFREAD("Proofread", "Fix spelling, grammar and punctuation. Keep the wording, tone and meaning otherwise unchanged."),
        FORMAL("Formal", "Rewrite this to sound more formal and professional."),
        CASUAL("Casual", "Rewrite this to sound more casual and relaxed."),
        SHORTER("Shorter", "Make this shorter and more concise while keeping the meaning."),
        FRIENDLY("Friendlier", "Rewrite this to sound warmer and friendlier."),
        EXPAND("Expand", "Expand this into a fuller, natural-sounding message."),
        EMOJIFY("Emojify", "Add a few fitting emoji to this message without changing the words."),
        TRANSLATE("Translate", ""),
        CUSTOM("Custom", ""),
    }

    class NoKey : Exception("Add a free Gemini or Groq API key in Settings › AI writing")

    private val system = "You are a writing assistant inside a phone keyboard. " +
        "Return ONLY the rewritten text: no quotes, no preamble, no explanations, no markdown. " +
        "Keep the same language as the input unless asked to translate. Preserve names, links and numbers."

    fun run(action: Action, text: String, extra: String = ""): String {
        val instruction = when (action) {
            Action.TRANSLATE -> "Translate this into ${extra.ifBlank { "English" }}."
            Action.CUSTOM -> extra.ifBlank { "Improve this." }
            else -> action.instruction
        }
        val user = "$instruction\n\nText:\n$text"

        val order = if (prefs.aiPrimary == "groq") listOf("groq", "gemini") else listOf("gemini", "groq")
        val available = order.filter { keyFor(it).isNotEmpty() }
        if (available.isEmpty()) throw NoKey()
        val tries = if (prefs.aiBackup) available else available.take(1)

        var first: Exception? = null
        for (p in tries) {
            try {
                val out = if (p == "gemini") gemini(user) else groq(user)
                return clean(out)
            } catch (e: Exception) {
                if (first == null) first = e
                // A rejected key won't get better by trying the backup with the same text.
                if (e is Http.HttpError && (e.code == 401 || e.code == 403)) break
            }
        }
        // Report the primary provider's error: that's the one to fix.
        throw first ?: Exception("AI request failed")
    }

    private fun keyFor(p: String) = if (p == "gemini") prefs.geminiKey else prefs.groqKey

    // ------------------------------------------------------------------
    // Gemini
    // ------------------------------------------------------------------

    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private fun keyHeader() = mapOf("x-goog-api-key" to prefs.geminiKey)

    /** "auto" (or the old built-in default) means: ask Google which models this key can use. */
    private val autoModel: Boolean
        get() = prefs.geminiModel.let { it == "auto" || it == "gemini-2.5-flash" }

    private fun gemini(user: String): String {
        if (!autoModel) return withRetry { geminiCall(prefs.geminiModel, user) }
        val ranked = rankedModels()
        var lastErr: Exception? = null
        // Try the best model, then the next ones if Google says a model is busy
        // (503/500/429) or unavailable to this key (404).
        for ((i, model) in ranked.take(4).withIndex()) {
            try {
                val out = if (i == 0) withRetry { geminiCall(model, user) } else geminiCall(model, user)
                prefs.sp.edit().putString("gemini_resolved_model", model).apply()
                return out
            } catch (e: Http.HttpError) {
                lastErr = e
                val tryNext = e.code in listOf(404, 429, 500, 503) ||
                    (e.code == 400 && e.body.contains("model", ignoreCase = true))
                if (!tryNext) throw e
                if (e.code == 404) prefs.sp.edit().remove("models_cache").apply()
            }
        }
        throw lastErr ?: Exception("No Gemini model answered")
    }

    /** One quick retry after a short pause when the service is momentarily busy. */
    private fun <T> withRetry(block: () -> T): T = try {
        block()
    } catch (e: Http.HttpError) {
        if (e.code == 503 || e.code == 500) { Thread.sleep(1200); block() } else throw e
    }

    /** Usable Flash models, best first; the last model that worked leads. Cached for a day. */
    private fun rankedModels(): List<String> {
        val now = System.currentTimeMillis()
        val cachedAt = prefs.sp.getLong("models_cache_at", 0)
        val cached = prefs.sp.getString("models_cache", null)
        val list = if (cached != null && now - cachedAt < 86_400_000L) cached.split(",").filter { it.isNotBlank() } else {
            val fresh = listModels()
            prefs.sp.edit().putString("models_cache", fresh.joinToString(",")).putLong("models_cache_at", now).apply()
            fresh
        }
        if (list.isEmpty()) throw Exception("Your Gemini key has no Flash models available")
        val last = prefs.sp.getString("gemini_resolved_model", null)
        return if (last != null && last in list) listOf(last) + list.filter { it != last } else list
    }

    private fun listModels(): List<String> {
        val res = JSONObject(Http.get("$base/models?pageSize=200", keyHeader()))
        val models = res.optJSONArray("models") ?: JSONArray()
        val skip = listOf("image", "tts", "audio", "live", "embedding", "thinking", "exp", "learnlm")
        val usable = ArrayList<String>()
        for (i in 0 until models.length()) {
            val m = models.optJSONObject(i) ?: continue
            val name = m.optString("name").removePrefix("models/")
            val methods = m.optJSONArray("supportedGenerationMethods")?.toString().orEmpty()
            val n = name.lowercase()
            if (!methods.contains("generateContent")) continue
            if (!n.startsWith("gemini") || !n.contains("flash")) continue
            if (skip.any { n.contains(it) }) continue
            usable += name
        }
        val versionRx = Regex("gemini-([0-9]+(?:\\.[0-9]+)?)")
        fun version(n: String) = versionRx.find(n)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        return usable.distinct().sortedWith(
            compareBy<String>({ it.contains("preview") }, { it.contains("lite") }, { -version(it) }, { it.length })
        )
    }

    private fun geminiCall(model: String, user: String): String {
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
            val gen = JSONObject().put("temperature", 0.4)
            // Turn off "thinking" on 2.5 Flash for speed (newer models choose their own).
            if (model.startsWith("gemini-2.5-flash")) gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            put("generationConfig", gen)
        }
        val res = JSONObject(Http.post("$base/models/$model:generateContent", body.toString(), keyHeader()))
        val cand = res.optJSONArray("candidates")?.optJSONObject(0)
        val parts = cand?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw Exception(
                if (cand?.optString("finishReason") == "SAFETY" || res.has("promptFeedback")) "Gemini declined to rewrite this text"
                else "Gemini returned no text"
            )
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.optJSONObject(i) ?: continue
            if (part.optBoolean("thought")) continue
            sb.append(part.optString("text"))
        }
        return sb.toString()
    }

    // ------------------------------------------------------------------
    // Groq
    // ------------------------------------------------------------------

    private fun groq(user: String): String {
        val body = JSONObject().apply {
            put("model", prefs.groqModel)
            put("temperature", 0.4)
            put("messages", JSONArray()
                .put(JSONObject().put("role", "system").put("content", system))
                .put(JSONObject().put("role", "user").put("content", user)))
        }
        val res = JSONObject(
            Http.post(
                "https://api.groq.com/openai/v1/chat/completions",
                body.toString(),
                mapOf("Authorization" to "Bearer ${prefs.groqKey}")
            )
        )
        return res.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content")
            ?: throw Exception("Groq returned no text")
    }

    private fun clean(s: String): String {
        var t = s.trim()
        if (t.startsWith("```")) t = t.removePrefix("```").substringAfter('\n').removeSuffix("```").trim()
        if (t.length >= 2 && t.first() == '"' && t.last() == '"') t = t.substring(1, t.length - 1)
        return t
    }

    companion object {
        fun friendlyError(e: Throwable): String {
            val server = (e as? Http.HttpError)?.let { serverMessage(it.body) }.orEmpty()
            val main = when {
                e is NoKey -> e.message ?: "Add an API key"
                e is Http.HttpError && e.code == 429 -> "Free limit reached. Try again in a minute."
                e is Http.HttpError && (e.code == 503 || e.code == 500) -> "Gemini is busy right now. Try again in a moment, or add a free Groq key as a backup in Settings › AI writing."
                e is Http.HttpError && (e.code == 401 || e.code == 403) -> "The API key was rejected (${e.code}). Check it in Settings › AI writing."
                e is Http.HttpError && e.code == 400 && server.contains("API key", ignoreCase = true) -> "The API key isn't valid. Check it in Settings › AI writing."
                e is Http.HttpError -> "The AI service returned an error (${e.code})."
                e is java.net.UnknownHostException -> "No internet connection"
                e is java.net.SocketTimeoutException -> "The AI service took too long to answer. Try again."
                else -> e.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach the AI service"
            }
            return if (server.isNotBlank()) "$main\n\n“$server”" else main
        }

        /** The human-readable message inside a Gemini/Groq error response. */
        private fun serverMessage(body: String): String = runCatching {
            val o = JSONObject(body)
            (o.optJSONObject("error")?.optString("message") ?: o.optString("message")).orEmpty().take(200)
        }.getOrDefault("")
    }
}
