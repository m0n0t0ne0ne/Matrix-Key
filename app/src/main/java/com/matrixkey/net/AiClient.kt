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

        var last: Exception? = null
        for (p in tries) {
            try {
                val out = if (p == "gemini") gemini(user) else groq(user)
                return clean(out)
            } catch (e: Exception) {
                last = e
                // Only fall through to the backup on limits / server errors / network trouble.
                if (e is Http.HttpError && e.code in 400..403 && e.code != 429) break
            }
        }
        throw last ?: Exception("AI request failed")
    }

    private fun keyFor(p: String) = if (p == "gemini") prefs.geminiKey else prefs.groqKey

    private fun gemini(user: String): String {
        val model = prefs.geminiModel
        val body = JSONObject().apply {
            put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
            put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
            val gen = JSONObject().put("temperature", 0.4)
            if (model.contains("flash")) gen.put("thinkingConfig", JSONObject().put("thinkingBudget", 0))
            put("generationConfig", gen)
        }
        val res = JSONObject(
            Http.post(
                "https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent",
                body.toString(),
                mapOf("x-goog-api-key" to prefs.geminiKey)
            )
        )
        val parts = res.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw Exception("Gemini returned no text")
        val sb = StringBuilder()
        for (i in 0 until parts.length()) sb.append(parts.optJSONObject(i)?.optString("text").orEmpty())
        return sb.toString()
    }

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
        fun friendlyError(e: Throwable): String = when {
            e is NoKey -> e.message ?: "Add an API key"
            e is Http.HttpError && e.code == 429 -> "Free limit reached. Try again in a minute."
            e is Http.HttpError && (e.code == 400 || e.code == 401 || e.code == 403) -> "The API key was rejected. Check it in Settings › AI writing."
            e is java.net.UnknownHostException -> "No internet connection"
            else -> "Couldn't reach the AI service"
        }
    }
}
