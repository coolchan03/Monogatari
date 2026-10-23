package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.os.Bundle
import android.text.Html
import android.view.KeyEvent
import com.google.android.material.button.MaterialButton
import android.view.MenuItem
import android.view.Menu
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private var narrator: ParagraphNarrator? = null
    /** Keep reading into the next chapter when a chapter read aloud finishes. */
    private var continuousNarration = false

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
        position = TextView(this).apply {
            gravity = android.view.Gravity.CENTER
            maxLines = 2
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_LabelMedium)
        }
        body = TextView(this).apply {
            textSize = 18f
            setPadding((20 * d).toInt(), (12 * d).toInt(), (20 * d).toInt(), (24 * d).toInt())
            setTextIsSelectable(true)
        }
        scroll = ScrollView(this).apply { addView(body) }
        // Bottom bar like the other readers: previous / chapter position / next. Text size and the chapter
        // list are in the toolbar menu.
        val nav = LinearLayout(this).apply {
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
        nav.addView(navButton(R.string.epub_previous_chapter) { showChapter(index - 1) }, LinearLayout.LayoutParams(-2, -2))
        nav.addView(position, LinearLayout.LayoutParams(0, -2, 1f))
        nav.addView(navButton(R.string.epub_next_chapter) { showChapter(index + 1) }, LinearLayout.LayoutParams(-2, -2))
        layout.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        layout.addView(nav, LinearLayout.LayoutParams(-1, -2))
        setMonogatariContent(layout, MonogatariChrome.TEXT_MAX_WIDTH_DP)
        // Parsing a large EPUB can take seconds; keep it off the main thread.
        lifecycleScope.launch {
            try {
                val parsed = withContext(Dispatchers.IO) {
                    if (file.length() > 128L * 1024 * 1024) error("EPUB exceeds 128 MB limit")
                    EpubBook.open(file)
                }
                book = parsed
                title = parsed.title
                body.textSize = preferences.getFloat("font_size", 18f)
                index = preferences.getInt(fileKey, 0)
                showChapter(index.coerceIn(0, (parsed.chapters.size - 1).coerceAtLeast(0)), restorePosition = true)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                position.text = getString(R.string.mono_epub_unable_to_open_epub)
                body.text = error.message
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_CHAPTERS, 0, R.string.epub_chapters)
            .setIcon(R.drawable.ic_list).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
        menu.add(0, MENU_SMALLER, 1, R.string.a11y_text_smaller)
        menu.add(0, MENU_LARGER, 2, R.string.a11y_text_larger)
        menu.add(0, MENU_READ_ALOUD, 3, R.string.epub_read_aloud)
        menu.add(0, MENU_SLEEP, 4, R.string.epub_sleep_timer)
        menu.add(0, MENU_SLEEP_CANCEL, 5, R.string.epub_sleep_timer_cancel)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_CHAPTERS -> showChapterPicker()
            MENU_SMALLER -> changeTextSize(-2f)
            MENU_LARGER -> changeTextSize(2f)
            MENU_READ_ALOUD -> {
                val engine = narrator ?: ParagraphNarrator(this) { readNextChapterAloud() }.also { narrator = it }
                if (engine.isSpeaking || continuousNarration) {
                    continuousNarration = false
                    engine.stop()
                } else {
                    continuousNarration = true
                    engine.speak(chapterParagraphs())
                }
            }
            MENU_SLEEP -> narrator?.sleepAfterMinutes(15)
            MENU_SLEEP_CANCEL -> narrator?.sleepAfterMinutes(0)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun chapterParagraphs(): List<String> = body.text.toString().split(Regex("\\n\\s*\\n|\\n"))

    private fun readNextChapterAloud() {
        val parsed = book ?: return
        if (!continuousNarration || index + 1 !in parsed.chapters.indices) {
            continuousNarration = false
            return
        }
        showChapter(index + 1)
        body.post { narrator?.speak(chapterParagraphs()) }
    }

    override fun onDestroy() {
        narrator?.shutdown()
        narrator = null
        super.onDestroy()
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
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle(R.string.epub_chapters)
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
        val savedFraction = if (restorePosition) preferences.getFloat("$fileKey-scroll-fraction", -1f) else 0f
        val savedY = if (restorePosition) preferences.getInt("$fileKey-scroll", 0) else 0
        scroll.post {
            // Prefer the width-independent fraction; older saves only have a pixel offset.
            val y = if (savedFraction >= 0f) {
                ReadingScrollPosition.offset(savedFraction, body.height, scroll.height)
            } else savedY
            scroll.scrollTo(0, y)
        }
        preferences.edit().putInt(fileKey, index).putInt("$fileKey-scroll", 0)
            .putFloat("$fileKey-scroll-fraction", 0f).apply()
    }

    override fun onPause() {
        super.onPause()
        if (book != null && fileKey.isNotEmpty() && ::scroll.isInitialized && body.height > 0) preferences.edit()
            .putInt(fileKey, index).putInt("$fileKey-scroll", scroll.scrollY)
            .putFloat("$fileKey-scroll-fraction", ReadingScrollPosition.fraction(scroll.scrollY, body.height, scroll.height))
            .apply()
    }

    private companion object {
        const val MENU_CHAPTERS = 7111
        const val MENU_SMALLER = 7112
        const val MENU_LARGER = 7113
        const val MENU_READ_ALOUD = 7101
        const val MENU_SLEEP = 7102
        const val MENU_SLEEP_CANCEL = 7103
    }
}
