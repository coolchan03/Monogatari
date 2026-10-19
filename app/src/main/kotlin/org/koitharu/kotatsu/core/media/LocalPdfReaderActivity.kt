package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.view.KeyEvent
import com.google.android.material.button.MaterialButton
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
        val d = resources.displayMetrics.density
        counter = TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
        }
        image = ImageView(this).apply { adjustViewBounds = true }
        // Same bottom bar as the EPUB reader: previous / page counter / next.
        val navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding((8 * d).toInt(), 0, (8 * d).toInt(), 0)
        }
        fun navButton(label: Int, onClick: () -> Unit) = MaterialButton(this, null,
            androidx.appcompat.R.attr.borderlessButtonStyle).apply {
            setText(label)
            minHeight = (48 * d).toInt()
            setOnClickListener { onClick() }
        }
        navigation.addView(navButton(R.string.pdf_previous_page) { display(pageIndex - 1) }, LinearLayout.LayoutParams(-2, -2))
        navigation.addView(counter, LinearLayout.LayoutParams(0, -2, 1f))
        navigation.addView(navButton(R.string.pdf_next_page) { display(pageIndex + 1) }, LinearLayout.LayoutParams(-2, -2))
        layout.addView(image, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(navigation, LinearLayout.LayoutParams(-1, -2))
        setMonogatariContent(layout)
        try {
            descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(descriptor!!)
            pageIndex = getSharedPreferences("pdf_progress", MODE_PRIVATE).getInt(fileKey, 0)
            display(pageIndex)
        } catch (error: Exception) {
            counter.text = "Unable to open PDF: ${error.message}"
        }
    }

    /** Keyboard and D-pad page turning for tablets, ChromeOS and laptops. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> { display(pageIndex + 1); true }
        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_PAGE_UP -> { display(pageIndex - 1); true }
        KeyEvent.KEYCODE_MOVE_HOME -> { display(0); true }
        KeyEvent.KEYCODE_MOVE_END -> { display((renderer?.pageCount ?: 1) - 1); true }
        else -> super.onKeyDown(keyCode, event)
    }

    private fun display(index: Int) {
        val pdf = renderer ?: return
        if (index !in 0 until pdf.pageCount) return
        pageIndex = index
        pdf.openPage(index).use { page ->
            val scale = (resources.displayMetrics.widthPixels.toFloat() / page.width).coerceIn(0.5f, 2.5f)
            val bitmap = Bitmap.createBitmap((page.width * scale).toInt(), (page.height * scale).toInt(), Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            image.setImageBitmap(bitmap)
        }
        counter.text = "Page ${index + 1} of ${pdf.pageCount}"
        image.contentDescription = "PDF page ${index + 1} of ${pdf.pageCount}"
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
