package org.koitharu.kotatsu.core.media

import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.jsoup.Jsoup
import org.koitharu.kotatsu.jsext.JsEpubBridge
import java.io.File

/** Reuses Monogatari's existing Mangayomi-compatible EPUB spine/TOC parser. */
class LocalEpubReaderActivity : AppCompatActivity() {
    private var book: JsEpubBridge.ParsedEpub? = null
    private var index = 0
    private var fileKey = ""
    private lateinit var body: TextView
    private lateinit var position: TextView
    private lateinit var scroll: ScrollView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = File(filesDir, "imported-books").canonicalFile
        val file = intent.getStringExtra("local_book_file")?.let { File(it).canonicalFile }
        if (file == null || file.parentFile != root || !file.isFile || file.extension.lowercase() != "epub") {
            finish()
            return
        }
        fileKey = file.name
        val layout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        position = TextView(this).apply { setPadding(16, 12, 16, 12) }
        body = TextView(this).apply { textSize = 18f; setPadding(24, 16, 24, 32); setTextIsSelectable(true) }
        scroll = ScrollView(this).apply { addView(body) }
        val nav = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        nav.addView(Button(this).apply {
            text = "Previous chapter"
            setOnClickListener { showChapter(index - 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(Button(this).apply {
            text = "Next chapter"
            setOnClickListener { showChapter(index + 1) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        layout.addView(position)
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(nav)
        setContentView(layout)
        try {
            if (file.length() > 128L * 1024 * 1024) error("EPUB exceeds 128 MB limit")
            book = JsEpubBridge().parse(file.readBytes())
            title = book?.title ?: file.name
            index = getSharedPreferences("epub_progress", MODE_PRIVATE).getInt(fileKey, 0)
            showChapter(index.coerceIn(0, (book!!.chapters.size - 1).coerceAtLeast(0)))
        } catch (error: Exception) {
            position.text = "Unable to open EPUB"
            body.text = error.message
        }
    }

    private fun showChapter(chapter: Int) {
        val parsed = book ?: return
        if (chapter !in parsed.chapters.indices) return
        index = chapter
        val item = parsed.chapters[chapter]
        position.text = "${item.title} (${chapter + 1}/${parsed.chapters.size})"
        body.text = Jsoup.parse(item.content).text()
        scroll.scrollTo(0, 0)
    }

    override fun onPause() {
        super.onPause()
        if (fileKey.isNotEmpty()) getSharedPreferences("epub_progress", MODE_PRIVATE)
            .edit().putInt(fileKey, index).apply()
    }
}
