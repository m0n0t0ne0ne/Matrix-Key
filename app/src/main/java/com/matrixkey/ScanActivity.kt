package com.matrixkey

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.preference.PreferenceManager
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.matrixkey.settings.mkButton
import com.matrixkey.settings.pageHeader
import java.io.File

/**
 * Scan text: take a photo (or pick one) and read the text in it on the
 * device. You can edit the result, then it's typed where your cursor was.
 */
class ScanActivity : AppCompatActivity() {

    companion object {
        const val PENDING = "ocr_pending"
        const val PENDING_AT = "ocr_pending_at"
        private const val REQ_CAMERA = 61
        private const val REQ_PICK = 62
    }

    private lateinit var status: TextView
    private lateinit var result: EditText
    private lateinit var insert: Button
    private lateinit var picker: TextSelectView
    private lateinit var pickerTools: LinearLayout
    private var photoUri: Uri? = null
    private val dp get() = resources.displayMetrics.density
    private fun px(v: Float) = (v * dp).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(px(16f), 0, px(16f), px(24f))
        }
        val (header, _) = pageHeader(this, "Scan text", showBack = true) { finish() }
        header.setPadding(0, header.paddingTop, 0, 0)
        col.addView(header)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        buttons.addView(mkButton(this, true).apply { text = "Take photo"; setOnClickListener { takePhoto() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8f) })
        buttons.addView(mkButton(this, false).apply {
            text = "Choose image"; setOnClickListener { pickImage() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(buttons)

        status = TextView(this).apply {
            text = "Take a photo of a sign, page or screen, or choose an image. Text is read on your phone and never uploaded."
            setTextColor(ContextCompat.getColor(this@ScanActivity, R.color.mk_sub))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(px(4f), px(16f), px(4f), px(12f))
        }
        col.addView(status)

        // The photo with its words outlined: tap or drag to choose what to insert.
        picker = TextSelectView(this, ContextCompat.getColor(this, R.color.mk_red)).apply {
            visibility = android.view.View.GONE
            onChange = { t -> result.setText(t); insert.isEnabled = t.isNotBlank() }
        }
        col.addView(picker, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = px(8f)
        })
        pickerTools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; visibility = android.view.View.GONE }
        pickerTools.addView(mkButton(this, false).apply { text = "Select all"; setOnClickListener { picker.selectAll(true) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8f) })
        pickerTools.addView(mkButton(this, false).apply { text = "Clear"; setOnClickListener { picker.selectAll(false) } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        col.addView(pickerTools, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = px(12f)
        })

        result = EditText(this).apply {
            hint = "Recognized text appears here. Edit it before inserting."
            gravity = Gravity.TOP
            minLines = 6
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(this@ScanActivity, R.color.mk_text))
            setPadding(px(16f), px(14f), px(16f), px(14f))
            background = GradientDrawable().apply {
                cornerRadius = 20 * dp
                setColor(ContextCompat.getColor(this@ScanActivity, R.color.mk_card))
            }
        }
        col.addView(result, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        insert = mkButton(this, true).apply {
            text = "Insert text"
            isEnabled = false
            setOnClickListener { done() }
        }
        col.addView(insert, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(16f)
        })

        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onDestroy() {
        // Don't keep the photo around once you're done with Scan text.
        if (isFinishing) File(cacheDir, "ocr").listFiles()?.forEach { it.delete() }
        super.onDestroy()
    }

    private fun takePhoto() {
        val dir = File(cacheDir, "ocr").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val f = File(dir, "scan_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(this, "com.matrixkey.files", f)
        photoUri = uri
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
            putExtra(MediaStore.EXTRA_OUTPUT, uri)
            addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_CAMERA)
        } catch (e: Exception) {
            status.text = "No camera app found. Choose an image instead."
        }
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT).apply { type = "image/*"; addCategory(Intent.CATEGORY_OPENABLE) }
        @Suppress("DEPRECATION")
        startActivityForResult(Intent.createChooser(i, "Choose image"), REQ_PICK)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != Activity.RESULT_OK) return
        val uri = when (requestCode) {
            REQ_CAMERA -> photoUri
            REQ_PICK -> data?.data
            else -> null
        } ?: return
        recognize(uri)
    }

    private fun recognize(uri: Uri) {
        status.text = "Reading text…"
        insert.isEnabled = false
        val image = try {
            InputImage.fromFilePath(this, uri)
        } catch (e: Exception) {
            status.text = "Couldn't open that image."
            return
        }
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).process(image)
            .addOnSuccessListener { text ->
                val t = text.text.trim()
                if (t.isEmpty()) {
                    status.text = "No text found. Try again closer, with more light."
                    picker.visibility = android.view.View.GONE; pickerTools.visibility = android.view.View.GONE
                } else {
                    status.text = "Tap words to add or remove them, or drag across the words you want. Edit below if you like, then tap Insert text."
                    result.setText(t)
                    insert.isEnabled = true
                    showPicker(uri, image.width, image.height, text)
                }
            }
            .addOnFailureListener {
                val m = it.message.orEmpty()
                status.text = if (m.contains("download", true))
                    "Google Play services is still downloading the text reader. Try again in a minute."
                else "Couldn't read text: $m"
            }
    }

    /** Shows the photo with each recognised word outlined for picking. */
    private fun showPicker(uri: Uri, photoWidth: Int, photoHeight: Int, text: com.google.mlkit.vision.text.Text) {
        val words = ArrayList<TextSelectView.Word>()
        var line = 0
        for (block in text.textBlocks) for (l in block.lines) {
            for (el in l.elements) el.boundingBox?.let { words += TextSelectView.Word(el.text, it, line) }
            line++
        }
        if (words.isEmpty()) return
        val bmp = runCatching {
            val src = android.graphics.ImageDecoder.createSource(contentResolver, uri)
            android.graphics.ImageDecoder.decodeBitmap(src) { d, info, _ ->
                d.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                val w = info.size.width
                if (w > 1600) d.setTargetSize(1600, (info.size.height * 1600f / w).toInt())
            }
        }.getOrNull() ?: return
        // Make sure the picture is the same way up as the image the words were read from.
        val shown = if ((bmp.width > bmp.height) != (photoWidth > photoHeight) && photoWidth != photoHeight)
            android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(90f) }, true)
        else bmp
        picker.set(shown, photoWidth, words)
        picker.visibility = android.view.View.VISIBLE
        pickerTools.visibility = android.view.View.VISIBLE
    }

    private fun done() {
        val t = result.text.toString().trim()
        if (t.isNotEmpty()) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(PENDING, t)
                .putLong(PENDING_AT, System.currentTimeMillis())
                .commit()
            MatrixKeyService.instance?.scanReady()
        }
        finish()
    }
}
