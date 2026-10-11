package org.koitharu.kotatsu.core.media

import android.os.Bundle
import android.text.Html
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Offline text and HTML reader with saved scroll position. */
class LocalTextReaderActivity : AppCompatActivity() {
    companion object { const val EXTRA_FILE = "local_book_file" }
    private lateinit var scroll: ScrollView
    private var book: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_FILE) ?: run { finish(); return }
        val root = File(filesDir, "imported-books").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile != root || !file.isFile) { finish(); return }
        book = file
        title = file.name.substringAfter('-', file.name)
        val text = TextView(this).apply {
            setPadding(32, 28, 32, 48)
            textSize = 18f
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply { addView(text) }
        setContentView(scroll)
        try {
            if (file.length() > 8L * 1024 * 1024) error("Text file is too large for this reader")
            val contents = file.readText()
            text.text = if (file.extension.lowercase() in setOf("html", "htm", "xhtml"))
                Html.fromHtml(contents, Html.FROM_HTML_MODE_COMPACT) else contents
            val y = getSharedPreferences("book_scroll", MODE_PRIVATE).getInt(file.name, 0)
            scroll.post { scroll.scrollTo(0, y) }
        } catch (error: Exception) {
            text.text = "Unable to read book: ${error.message}"
        }
    }

    override fun onPause() {
        super.onPause()
        book?.let { file ->
            getSharedPreferences("book_scroll", MODE_PRIVATE).edit()
                .putInt(file.name, scroll.scrollY).apply()
        }
    }
}
