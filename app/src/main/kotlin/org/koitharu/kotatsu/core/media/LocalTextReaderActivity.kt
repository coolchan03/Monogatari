package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.os.Bundle
import android.text.Html
import android.speech.tts.TextToSpeech
import android.view.KeyEvent
import android.view.Menu
import android.view.View
import android.view.MenuItem
import java.util.Locale
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/** Offline text and HTML reader with saved scroll position. */
class LocalTextReaderActivity : AppCompatActivity() {
    companion object { const val EXTRA_FILE = "local_book_file" }
    private lateinit var scroll: ScrollView
    private var book: File? = null
    private var tts: TextToSpeech? = null
    private var spokenParagraphs = emptyList<String>()
    private var paragraphIndex = 0
    private var ttsReady = false
    private var speechGeneration = 0
    private var sleepEndAt = Long.MAX_VALUE

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
            tts = TextToSpeech(this) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) tts?.language = Locale.getDefault()
            }
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
        if (item.itemId == 6702) {
            sleepEndAt = android.os.SystemClock.elapsedRealtime() + 15L * 60_000L
            return true
        }
        if (item.itemId == 6703) {
            sleepEndAt = Long.MAX_VALUE
            return true
        }
        if (item.itemId != 6701) return super.onOptionsItemSelected(item)
        val engine = tts ?: return true
        if (engine.isSpeaking) { speechGeneration++; engine.stop(); return true }
        if (!ttsReady || spokenParagraphs.isEmpty()) return true
        paragraphIndex = 0
        val generation = ++speechGeneration
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    if (generation != speechGeneration || isFinishing || isDestroyed) return@runOnUiThread
                    paragraphIndex++
                    if (paragraphIndex < spokenParagraphs.size &&
                        android.os.SystemClock.elapsedRealtime() < sleepEndAt) speakNext()
                }
            }
        })
        speakNext()
        return true
    }

    private fun speakNext() {
        if (android.os.SystemClock.elapsedRealtime() >= sleepEndAt) return
        tts?.speak(spokenParagraphs[paragraphIndex], TextToSpeech.QUEUE_FLUSH, null, "text-$paragraphIndex")
    }

    override fun onDestroy() {
        speechGeneration++
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
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
