package org.koitharu.kotatsu.core.media

import android.os.Bundle
import android.text.Html
import android.speech.tts.TextToSpeech
import android.view.Menu
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
            spokenParagraphs = contents.split(Regex("\\n\\s*\\n")).map { it.trim() }
                .filter { it.isNotEmpty() }.take(2000)
            tts = TextToSpeech(this) { status ->
                ttsReady = status == TextToSpeech.SUCCESS
                if (ttsReady) tts?.language = Locale.getDefault()
            }
            text.text = if (file.extension.lowercase() in setOf("html", "htm", "xhtml"))
                Html.fromHtml(contents, Html.FROM_HTML_MODE_COMPACT) else contents
            val y = getSharedPreferences("book_scroll", MODE_PRIVATE).getInt(file.name, 0)
            scroll.post { scroll.scrollTo(0, y) }
        } catch (error: Exception) {
            text.text = "Unable to read book: ${error.message}"
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 6701, 0, "Read aloud / Stop").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != 6701) return super.onOptionsItemSelected(item)
        val engine = tts ?: return true
        if (engine.isSpeaking) { engine.stop(); return true }
        if (!ttsReady || spokenParagraphs.isEmpty()) return true
        paragraphIndex = 0
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onError(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                runOnUiThread {
                    paragraphIndex++
                    if (paragraphIndex < spokenParagraphs.size) speakNext()
                }
            }
        })
        speakNext()
        return true
    }

    private fun speakNext() {
        tts?.speak(spokenParagraphs[paragraphIndex], TextToSpeech.QUEUE_FLUSH, null, "text-$paragraphIndex")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        book?.let { file ->
            getSharedPreferences("book_scroll", MODE_PRIVATE).edit()
                .putInt(file.name, scroll.scrollY).apply()
        }
    }
}
