package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.os.Bundle
import android.text.Html
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.MenuItem
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.koitharu.kotatsu.stats.activity.ReadingTimer
import java.io.File

/** Offline text and HTML reader with saved scroll position. */
class LocalTextReaderActivity : AppCompatActivity() {
    companion object { const val EXTRA_FILE = "local_book_file" }
    private lateinit var scroll: ScrollView
    private var book: File? = null
    private var narrator: ParagraphNarrator? = null
    private var spokenParagraphs = emptyList<String>()
    private val readingTimer by lazy { ReadingTimer(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(EXTRA_FILE) ?: run { finish(); return }
        val root = File(filesDir, "imported-books").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile != root || !file.isFile) { finish(); return }
        book = file
        title = file.name.substringAfter('-', file.name)
        val text = TextView(this).apply {
            val d = resources.displayMetrics.density
            setPadding((20 * d).toInt(), (16 * d).toInt(), (20 * d).toInt(), (32 * d).toInt())
            textSize = 18f
            setTextIsSelectable(true)
        }
        // Focusable so Page Up/Down, Space and arrow keys scroll on keyboards (tablets, ChromeOS, laptops).
        scroll = ScrollView(this).apply { addView(text); isFocusable = true; isFocusableInTouchMode = false }
        setMonogatariContent(scroll, MonogatariChrome.TEXT_MAX_WIDTH_DP)
        try {
            if (file.length() > 8L * 1024 * 1024) error("Text file is too large for this reader")
            val contents = file.readText()
            val readable = if (file.extension.lowercase() in setOf("html", "htm", "xhtml"))
                Html.fromHtml(contents, Html.FROM_HTML_MODE_COMPACT).toString() else contents
            spokenParagraphs = readable.split(Regex("\\n\\s*\\n")).map { it.trim() }
                .filter { it.isNotEmpty() }.take(2000)
            text.text = readable
            val prefs = getSharedPreferences("book_scroll", MODE_PRIVATE)
            val y = prefs.getInt(file.name, 0)
            val fraction = prefs.getFloat("${file.name}#fraction", -1f)
            // The fraction survives rotation, folding and window resizing; the pixel offset is the legacy fallback.
            scroll.post {
                scroll.scrollTo(0, if (fraction >= 0f) ReadingScrollPosition.offset(fraction, text.height, scroll.height) else y)
            }
        } catch (error: Exception) {
            text.text = "Unable to read book: ${error.message}"
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_PAGE_DOWN, KeyEvent.KEYCODE_SPACE -> scroll.pageScroll(View.FOCUS_DOWN)
        KeyEvent.KEYCODE_PAGE_UP -> scroll.pageScroll(View.FOCUS_UP)
        KeyEvent.KEYCODE_MOVE_HOME -> scroll.fullScroll(View.FOCUS_UP)
        KeyEvent.KEYCODE_MOVE_END -> scroll.fullScroll(View.FOCUS_DOWN)
        else -> super.onKeyDown(keyCode, event)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 6701, 0, "Read aloud / Stop").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 6702, 1, "Sleep timer: 15 min")
        menu.add(0, 6703, 2, "Cancel sleep timer")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            6701 -> {
                // The speech engine is only started once the user asks for it.
                val engine = narrator ?: ParagraphNarrator(this).also { narrator = it }
                if (engine.isSpeaking) engine.stop() else engine.speak(spokenParagraphs)
            }
            6702 -> (narrator ?: ParagraphNarrator(this).also { narrator = it }).sleepAfterMinutes(15)
            6703 -> narrator?.sleepAfterMinutes(0)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onDestroy() {
        narrator?.shutdown()
        narrator = null
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        readingTimer.resume()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        readingTimer.interaction()
    }

    override fun onPause() {
        super.onPause()
        readingTimer.pause()
        // Before the first layout there is no real position yet; keep the saved one.
        if (::scroll.isInitialized && (scroll.getChildAt(0)?.height ?: 0) == 0) return
        book?.let { file ->
            getSharedPreferences("book_scroll", MODE_PRIVATE).edit()
                .putInt(file.name, scroll.scrollY)
                .putFloat("${file.name}#fraction", ReadingScrollPosition.fraction(scroll.scrollY, scroll.getChildAt(0)?.height ?: 0, scroll.height))
                .apply()
        }
    }
}
