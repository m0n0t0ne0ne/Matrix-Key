package com.matrixkey.net

import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Tiny blocking HTTP helper (always call off the main thread). */
object Http {
    class HttpError(val code: Int, val body: String) : Exception("HTTP $code: ${body.take(300)}")

    fun get(url: String, headers: Map<String, String> = emptyMap()): String =
        String(request("GET", url, headers, null), Charsets.UTF_8)

    fun post(url: String, body: String, headers: Map<String, String> = emptyMap()): String =
        String(request("POST", url, headers + ("Content-Type" to "application/json"), body.toByteArray()), Charsets.UTF_8)

    fun bytes(url: String, maxBytes: Int = 8 * 1024 * 1024): ByteArray = request("GET", url, emptyMap(), null, maxBytes)

    private fun request(method: String, url: String, headers: Map<String, String>, body: ByteArray?, maxBytes: Int = 4 * 1024 * 1024): ByteArray {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 12000
            c.readTimeout = 30000
            c.setRequestProperty("User-Agent", "MatrixKey/0.1 (Android)")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.outputStream.use { it.write(body) }
            }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val out = ByteArrayOutputStream()
            stream?.use { s ->
                val buf = ByteArray(16 * 1024)
                while (true) {
                    val n = s.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > maxBytes) throw HttpError(413, "Response too large")
                }
            }
            if (code !in 200..299) throw HttpError(code, out.toString("UTF-8"))
            return out.toByteArray()
        } finally {
            c.disconnect()
        }
    }
}
