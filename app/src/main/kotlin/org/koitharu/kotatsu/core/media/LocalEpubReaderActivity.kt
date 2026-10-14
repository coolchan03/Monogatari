package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.os.Bundle
import android.text.Html
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.koitharu.kotatsu.core.media.epub.EpubBook
import java.io.File

/** Reuses Monogatari's existing Mangayomi-compatible EPUB spine/TOC parser. */
class LocalEpubReaderActivity : AppCompatActivity() {
    private var book: EpubBook? = null
    private var index = 0
    private var fileKey = ""
    private var chapterScrollY = 0
    private val preferences by lazy { getSharedPreferences("epub_progress", MODE_PRIVATE) }
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
        val d = resources.displayMetrics.density
        position = TextView(this).apply { setPadding((16 * d).toInt(), (8 * d).toInt(), (16 * d).toInt(), (8 * d).toInt()) }
        body = TextView(this).apply {
            textSize = 18f
            setPadding((20 * d).toInt(), (12 * d).toInt(), (20 * d).toInt(), (24 * d).toInt())
            setTextIsSelectable(true)
        }
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
        val appearance = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        appearance.addView(Button(this).apply {
            text = "A-"
            setOnClickListener { changeTextSize(-2f) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        appearance.addView(Button(this).apply {
            text = "A+"
            setOnClickListener { changeTextSize(2f) }
        }, LinearLayout.LayoutParams(0, -2, 1f))
        appearance.addView(Button(this).apply {
            text = "Chapters"
            setOnClickListener { showChapterPicker() }
        }, LinearLayout.LayoutParams(0, -2, 2f))
        layout.addView(appearance)
        layout.addView(position)
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(nav)
        setMonogatariContent(layout, MonogatariChrome.TEXT_MAX_WIDTH_DP)
        try {
            if (file.length() > 128L * 1024 * 1024) error("EPUB exceeds 128 MB limit")
            book = EpubBook.open(file)
            title = book?.title ?: file.name
            body.textSize = preferences.getFloat("font_size", 18f)
            index = preferences.getInt(fileKey, 0)
            showChapter(index.coerceIn(0, (book!!.chapters.size - 1).coerceAtLeast(0)), restorePosition = true)
        } catch (error: Exception) {
            position.text = "Unable to open EPUB"
            body.text = error.message
        }
    }

    /** Keyboard: Page Up/Down and Space scroll, arrow keys or [ ] change chapter, Ctrl +/- change text size. */
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val handled = when (keyCode) {
            KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> scroll.pageScroll(View.FOCUS_DOWN)
            KeyEvent.KEYCODE_PAGE_UP -> scroll.pageScroll(View.FOCUS_UP)
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_RIGHT_BRACKET -> { showChapter(index + 1); true }
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_LEFT_BRACKET -> { showChapter(index - 1); true }
            KeyEvent.KEYCODE_EQUALS, KeyEvent.KEYCODE_PLUS -> if (event.isCtrlPressed) { changeTextSize(2f); true } else false
            KeyEvent.KEYCODE_MINUS -> if (event.isCtrlPressed) { changeTextSize(-2f); true } else false
            else -> false
        }
        return handled || super.onKeyDown(keyCode, event)
    }

    private fun changeTextSize(delta: Float) {
        body.textSize = (body.textSize / resources.displayMetrics.scaledDensity + delta).coerceIn(12f, 34f)
        preferences.edit().putFloat("font_size", body.textSize / resources.displayMetrics.scaledDensity).apply()
    }

    private fun showChapterPicker() {
        val chapters = book?.chapters ?: return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Chapters")
            .setItems(chapters.map { it.title }.toTypedArray()) { _, selected -> showChapter(selected) }
            .show()
    }

    private fun showChapter(chapter: Int, restorePosition: Boolean = false) {
        val parsed = book ?: return
        if (chapter !in parsed.chapters.indices) return
        index = chapter
        val item = parsed.chapters[chapter]
        position.text = "${item.title} (${chapter + 1}/${parsed.chapters.size})"
        body.text = Html.fromHtml(item.content, Html.FROM_HTML_MODE_COMPACT)
        val savedY = if (restorePosition) preferences.getInt("$fileKey-scroll", 0) else 0
        scroll.post { scroll.scrollTo(0, savedY) }
        preferences.edit().putInt(fileKey, index).putInt("$fileKey-scroll", 0).apply()
    }

    override fun onPause() {
        super.onPause()
        if (fileKey.isNotEmpty() && ::scroll.isInitialized) preferences.edit()
            .putInt(fileKey, index).putInt("$fileKey-scroll", scroll.scrollY).apply()
    }
}
