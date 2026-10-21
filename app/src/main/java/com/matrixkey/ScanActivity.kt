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
        buttons.addView(Button(this).apply { text = "Take photo"; setOnClickListener { takePhoto() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = px(8f) })
        buttons.addView(Button(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
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

        insert = Button(this).apply {
            text = "Insert text"
            isEnabled = false
            setOnClickListener { done() }
        }
        col.addView(insert, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = px(16f)
        })

        setContentView(ScrollView(this).apply { addView(col) })
        if (savedInstanceState == null) takePhoto()
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
                } else {
                    status.text = "Found text. Edit it if you like, then tap Insert text."
                    result.setText(t)
                    insert.isEnabled = true
                }
            }
            .addOnFailureListener { status.text = "Couldn't read text: ${it.message}" }
    }

    private fun done() {
        val t = result.text.toString().trim()
        if (t.isNotEmpty()) {
            PreferenceManager.getDefaultSharedPreferences(this).edit()
                .putString(PENDING, t)
                .putLong(PENDING_AT, System.currentTimeMillis())
                .apply()
        }
        finish()
    }
}
