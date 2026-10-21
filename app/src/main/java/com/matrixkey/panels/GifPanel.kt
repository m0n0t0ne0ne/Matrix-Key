package com.matrixkey.panels

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.matrixkey.net.GifClient
import com.matrixkey.net.Http
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService

@SuppressLint("ViewConstructor")
class GifPanel(
    context: Context,
    private val ui: PanelUi,
    private val client: GifClient,
    private val executor: ExecutorService,
    private val onSend: (GifClient.Gif) -> Unit,
    private val onSearchTap: () -> Unit,
    private val onClose: () -> Unit,
) : LinearLayout(context) {

    private val main = Handler(Looper.getMainLooper())
    private val searchPill = TextView(context)
    private val status = ui.text("", 14f, ui.theme.sub)
    private val attribution = ui.text("", 11f, ui.theme.sub)
    private val columns = ui.row()
    private val left = ui.column()
    private val right = ui.column()
    private var generation = 0

    init {
        orientation = VERTICAL
        setPadding(ui.px(8f), ui.px(4f), ui.px(8f), 0)

        val header = ui.row()
        header.addView(ui.chip("ABC") { onClose() }, LayoutParams(LayoutParams.WRAP_CONTENT, ui.px(40f)))
        header.addView(ui.gap(8f))
        searchPill.apply {
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ui.px(16f), 0, ui.px(16f), 0)
            setTextColor(ui.theme.sub)
            ui.pressable(this, ui.theme.key, 20f)
            setOnClickListener { ui.haptic(it); onSearchTap() }
        }
        header.addView(searchPill, LayoutParams(0, ui.px(40f), 1f))
        addView(header, LayoutParams(LayoutParams.MATCH_PARENT, ui.px(48f)))

        attribution.gravity = Gravity.END
        addView(attribution, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { setMargins(0, 0, ui.px(6f), ui.px(2f)) })

        status.gravity = Gravity.CENTER
        addView(status, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))

        columns.gravity = Gravity.TOP
        columns.addView(left, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        columns.addView(right, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        val scroll = ScrollView(context)
        scroll.addView(columns)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun search(query: String) {
        attribution.text = client.attribution
        searchPill.text = query.ifBlank { "Search ${client.providerName}" }
        searchPill.setTextColor(if (query.isBlank()) ui.theme.sub else ui.theme.text)
        status.text = if (query.isBlank()) "Trending" else "Searching…"
        left.removeAllViews(); right.removeAllViews()
        val gen = ++generation
        executor.execute {
            val result = runCatching { client.search(query) }
            main.post {
                if (gen != generation) return@post
                result.onSuccess { gifs ->
                    status.text = if (gifs.isEmpty()) "No GIFs found" else if (query.isBlank()) "Trending" else ""
                    status.visibility = if (status.text.isEmpty()) GONE else VISIBLE
                    show(gifs, gen)
                }.onFailure { e ->
                    status.visibility = VISIBLE
                    status.text = when (e) {
                        is GifClient.MissingKey -> e.message
                        is Http.HttpError -> when (e.code) {
                            429 -> "Hourly GIF limit reached. Try again later."
                            401, 403, 404 -> "${client.providerName} rejected the API key (${e.code}). Check the key in Settings › GIFs." +
                                GifClient.serverMessage(e.body)
                            else -> "${client.providerName} error (${e.code})." + GifClient.serverMessage(e.body)
                        }
                        else -> "Couldn't load GIFs. Check your connection."
                    }
                }
            }
        }
    }

    private fun show(gifs: List<GifClient.Gif>, gen: Int) {
        var lh = 0f; var rh = 0f
        val colW = (width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) / 2f - ui.px(8f)
        for (g in gifs) {
            val h = (colW * g.height / g.width.coerceAtLeast(1)).coerceIn(ui.px(70f).toFloat(), ui.px(220f).toFloat())
            val iv = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = "GIF"
                clipToOutline = true
                background = ui.rounded(ui.theme.key, 10f)
                setOnClickListener { ui.haptic(it); onSend(g) }
            }
            val target = if (lh <= rh) left.also { lh += h } else right.also { rh += h }
            target.addView(iv, LayoutParams(LayoutParams.MATCH_PARENT, h.toInt()).apply { setMargins(ui.px(3f), ui.px(3f), ui.px(3f), ui.px(3f)) })
            load(g.previewUrl, iv, gen)
        }
    }

    private fun load(url: String, iv: ImageView, gen: Int) {
        cache.get(url)?.let { decode(it, iv); return }
        executor.execute {
            val bytes = runCatching { Http.bytes(url, 3 * 1024 * 1024) }.getOrNull() ?: return@execute
            cache.put(url, bytes)
            main.post { if (gen == generation) decode(bytes, iv) }
        }
    }

    private fun decode(bytes: ByteArray, iv: ImageView) {
        val d: Drawable = runCatching { ImageDecoder.decodeDrawable(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) }.getOrNull() ?: return
        iv.setImageDrawable(d)
        (d as? AnimatedImageDrawable)?.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        generation++
    }

    companion object {
        private val cache = object : LruCache<String, ByteArray>(16 * 1024 * 1024) {
            override fun sizeOf(key: String, value: ByteArray) = value.size
        }
    }
}
