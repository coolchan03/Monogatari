package org.koitharu.kotatsu.core.media

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Native offline PDF renderer. Saves last page for each imported file. */
class LocalPdfReaderActivity : AppCompatActivity() {
    private var descriptor: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private var pageIndex = 0
    private lateinit var image: ImageView
    private lateinit var counter: TextView
    private var fileKey = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = File(filesDir, "imported-books").canonicalFile
        val file = intent.getStringExtra("local_book_file")?.let { File(it).canonicalFile }
        if (file == null || file.parentFile != root || !file.isFile || file.extension.lowercase() != "pdf") {
            finish()
            return
        }
        title = file.name.substringAfter('-', file.name)
        fileKey = file.name
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        counter = TextView(this).apply { textSize = 16f; setPadding(16, 12, 16, 12) }
        image = ImageView(this).apply { adjustViewBounds = true }
        val navigation = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        navigation.addView(Button(this).apply {
            text = "Previous"
            setOnClickListener { display(pageIndex - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(Button(this).apply {
            text = "Next"
            setOnClickListener { display(pageIndex + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(counter)
        layout.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(navigation)
        setContentView(layout)
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
            pageIndex = getSharedPreferences("pdf_progress", MODE_PRIVATE).getInt(fileKey, 0)
            display(pageIndex)
        } catch (error: Exception) {
            counter.text = "Unable to open PDF: ${error.message}"
        }
    }

    private fun display(index: Int) {
        val pdf = renderer ?: return
        if (index !in 0 until pdf.pageCount) return
        pageIndex = index
        pdf.openPage(index).use { page ->
            val scale = (resources.displayMetrics.widthPixels.toFloat() / page.width).coerceIn(0.5f, 3f)
            val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.setImageBitmap(bitmap)
        }
        counter.text = "Page ${index + 1} of ${pdf.pageCount}"
    }

    override fun onPause() {
        super.onPause()
        if (fileKey.isNotEmpty()) getSharedPreferences("pdf_progress", MODE_PRIVATE)
            .edit().putInt(fileKey, pageIndex).apply()
    }

    override fun onDestroy() {
        renderer?.close()
        descriptor?.close()
        super.onDestroy()
    }
}
