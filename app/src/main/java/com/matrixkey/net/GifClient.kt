package com.matrixkey.net

import android.content.Context
import androidx.preference.PreferenceManager
import com.matrixkey.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

/**
 * GIF search through GIPHY or KLIPY. Any provider with a search API can be
 * added by implementing another branch here.
 */
class GifClient(private val context: Context, private val prefs: Prefs) {

    data class Gif(
        val id: String,
        /** Small animated file shown in the grid. */
        val previewUrl: String,
        /** File sent into the conversation. */
        val sendUrl: String,
        /** Web page link, used when an app can't take GIFs directly. */
        val pageUrl: String,
        val width: Int,
        val height: Int,
    )

    class MissingKey(provider: String) : Exception("Add your free $provider API key in Settings › GIFs")

    val providerName get() = if (prefs.gifProvider == "klipy") "KLIPY" else "GIPHY"
    val attribution get() = "Powered by $providerName"

    private val customerId: String by lazy {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        sp.getString("gif_customer_id", null) ?: UUID.randomUUID().toString().also {
            sp.edit().putString("gif_customer_id", it).apply()
        }
    }

    /** Empty query = trending. */
    fun search(query: String): List<Gif> =
        if (prefs.gifProvider == "klipy") klipy(query) else giphy(query)

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun giphy(query: String): List<Gif> {
        val key = prefs.giphyKey.ifEmpty { throw MissingKey("GIPHY") }
        val base = "https://api.giphy.com/v1/gifs"
        val url = if (query.isBlank()) "$base/trending?api_key=${enc(key)}&limit=30&rating=${enc(prefs.gifRating)}"
        else "$base/search?api_key=${enc(key)}&q=${enc(query)}&limit=30&rating=${enc(prefs.gifRating)}&lang=en"
        val data = JSONObject(Http.get(url)).optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Gif>()
        for (i in 0 until data.length()) {
            val o = data.getJSONObject(i)
            val images = o.optJSONObject("images") ?: continue
            val preview = images.optJSONObject("fixed_width_downsampled") ?: images.optJSONObject("fixed_width") ?: continue
            val send = images.optJSONObject("downsized") ?: images.optJSONObject("fixed_width") ?: preview
            val pUrl = preview.optString("webp").ifEmpty { preview.optString("url") }
            val sUrl = send.optString("url")
            if (pUrl.isEmpty() || sUrl.isEmpty()) continue
            out += Gif(
                o.optString("id"), pUrl, sUrl, o.optString("url").ifEmpty { sUrl },
                preview.optString("width").toIntOrNull() ?: 200, preview.optString("height").toIntOrNull() ?: 200
            )
        }
        return out
    }

    private fun klipy(query: String): List<Gif> {
        val key = prefs.klipyKey.replace(Regex("\\s"), "").ifEmpty { throw MissingKey("KLIPY") }
        val base = "https://api.klipy.com/api/v1/${enc(key)}/gifs"
        // Keep to the parameters KLIPY documents; extra ones can make it reject the call.
        val common = "per_page=30&page=1&customer_id=${enc(customerId)}"
        val url = if (query.isBlank()) "$base/trending?$common" else "$base/search?q=${enc(query)}&$common"
        val body = try {
            Http.get(url)
        } catch (e: Http.HttpError) {
            // Some keys don't have trending enabled: fall back to a search.
            if (query.isBlank() && e.code == 404) Http.get("$base/search?q=${enc("trending")}&$common") else throw e
        }
        val root = JSONObject(body)
        val outer = root.optJSONObject("data")
        val items: JSONArray = outer?.optJSONArray("data") ?: root.optJSONArray("data") ?: return emptyList()
        val out = ArrayList<Gif>()
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            if (o.optString("type") == "ad") continue
            val files = o.optJSONObject("file") ?: o.optJSONObject("files") ?: o
            val previewSize = firstObj(files, "sm", "xs", "md", "hd")
            val sendSize = firstObj(files, "md", "hd", "sm", "xs")
            val p = previewSize?.let { firstObj(it, "webp", "gif") }
            val s = sendSize?.let { firstObj(it, "gif", "webp") }
            val pUrl = p?.optString("url").orEmpty().ifEmpty { findUrl(files, ".gif") ?: "" }
            val sUrl = s?.optString("url").orEmpty().ifEmpty { findUrl(files, ".gif") ?: pUrl }
            if (pUrl.isEmpty()) continue
            out += Gif(
                o.optString("id", o.optString("slug")), pUrl, sUrl,
                o.optString("url").ifEmpty { sUrl },
                p?.optInt("width", 200) ?: 200, p?.optInt("height", 200) ?: 200
            )
        }
        return out
    }

    companion object {
        /** A short human-readable message from an API error body, if it has one. */
        fun serverMessage(body: String): String {
            val msg = runCatching {
                val o = JSONObject(body)
                o.optString("message").ifEmpty { o.optJSONObject("error")?.optString("message").orEmpty() }
                    .ifEmpty { o.optJSONObject("meta")?.optString("msg").orEmpty() }
                    .ifEmpty { o.optJSONArray("errors")?.optString(0).orEmpty() }
            }.getOrNull().orEmpty()
            return if (msg.isNotBlank()) "\n\"${msg.take(120)}\"" else ""
        }
    }

    private fun firstObj(o: JSONObject, vararg keys: String): JSONObject? {
        for (k in keys) o.optJSONObject(k)?.let { return it }
        return null
    }

    /** Depth-first search for any URL string ending with [ext]. */
    private fun findUrl(o: Any?, ext: String): String? {
        when (o) {
            is JSONObject -> {
                val it = o.keys()
                while (it.hasNext()) findUrl(o.opt(it.next()), ext)?.let { u -> return u }
            }
            is JSONArray -> for (i in 0 until o.length()) findUrl(o.opt(i), ext)?.let { return it }
            is String -> if (o.startsWith("http") && o.substringBefore('?').endsWith(ext)) return o
        }
        return null
    }
}
