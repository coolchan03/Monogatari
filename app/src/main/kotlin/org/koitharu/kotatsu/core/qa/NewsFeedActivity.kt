package org.koitharu.kotatsu.core.qa

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/** User-initiated, HTTPS-only RSS/Atom feeds with locally saved followed-tag filters. */
class NewsFeedActivity : AppCompatActivity() {
    private lateinit var feedInput: EditText
    private lateinit var tagInput: EditText
    private lateinit var message: TextView
    private lateinit var entries: LinearLayout
    private var items = emptyList<NewsFeedParser.Entry>()
    private var onlyFollowing = false
    private var feedJob: Job? = null
    private val prefs by lazy { getSharedPreferences("monogatari_news", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "News & followed tags"
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        fun action(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
            this.text = text
            setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        feedInput = EditText(this).apply {
            hint = "HTTPS RSS/Atom feed URL"
            setSingleLine(true)
            setText(prefs.getString("selected", "https://www.animenewsnetwork.com/news/rss.xml"))
        }
        root.addView(feedInput, LinearLayout.LayoutParams(-1, -2))
        action("Save feed and refresh") { saveAndLoad() }
        action("Saved feeds / remove feed") { showSavedFeeds() }
        tagInput = EditText(this).apply {
            hint = "Tags to follow, separated by commas"
            setSingleLine(true)
            setText(followedTags().sorted().joinToString(", "))
        }
        root.addView(tagInput, LinearLayout.LayoutParams(-1, -2))
        action("Save followed tags") {
            val tags = tagInput.text.toString().split(',')
                .map { it.trim() }.filter { it.length in 2..50 }.take(40).toSet()
            prefs.edit().putStringSet("tags", tags).apply()
            message.text = "Saved ${tags.size} followed tags on this device"
            render()
        }
        action("Show all / followed only") {
            onlyFollowing = !onlyFollowing
            render()
        }
        message = TextView(this).apply { text = "Choose a feed and refresh" }
        root.addView(message, LinearLayout.LayoutParams(-1, -2))
        val listScroll = ScrollView(this)
        entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(entries)
        root.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun followedTags(): Set<String> = prefs.getStringSet("tags", emptySet()).orEmpty().toSet()

    private fun saveAndLoad() {
        val url = feedInput.text.toString().trim()
        if (!NewsFeedPolicy.trustedFeed(url)) {
            message.text = "For safety, use an approved HTTPS RSS feed (Anime News Network, YouTube, or Reddit)."
            return
        }
        val feeds = prefs.getStringSet("feeds", emptySet()).orEmpty().toMutableSet()
        if (feeds.size >= 25 && url !in feeds) {
            message.text = "Remove a saved feed before adding another"
            return
        }
        feeds += url
        prefs.edit().putStringSet("feeds", feeds).putString("selected", url).apply()
        load(url)
    }

    private fun showSavedFeeds() {
        val feeds = prefs.getStringSet("feeds", emptySet()).orEmpty().sorted()
        if (feeds.isEmpty()) { message.text = "No saved feeds"; return }
        AlertDialog.Builder(this).setTitle("Saved feeds")
            .setItems(feeds.toTypedArray()) { _, which ->
                val selected = feeds[which]
                AlertDialog.Builder(this).setTitle(selected)
                    .setPositiveButton("Open") { _, _ -> feedInput.setText(selected); load(selected) }
                    .setNegativeButton("Remove") { _, _ ->
                        prefs.edit().putStringSet("feeds", feeds.toMutableSet().apply { remove(selected) }).apply()
                        message.text = "Feed removed"
                    }
                    .setNeutralButton(android.R.string.cancel, null).show()
            }.show()
    }

    private fun load(url: String) {
        if (!NewsFeedPolicy.trustedFeed(url)) return
        feedJob?.cancel()
        items = emptyList()
        entries.removeAllViews()
        message.text = "Loading feed…"
        feedJob = lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { fetchEntries(url) } }
            if (url != feedInput.text.toString().trim()) return@launch
            result.onSuccess { loaded ->
                items = loaded
                message.text = "Loaded ${loaded.size} entries — nothing is fetched in the background"
                render()
            }.onFailure { error ->
                message.text = "Feed unavailable: ${error.message}"
            }
        }
    }

    private fun fetchEntries(url: String): List<NewsFeedParser.Entry> {
        require(NewsFeedPolicy.trustedFeed(url))
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10_000
            conn.readTimeout = 12_000
            conn.setRequestProperty("Accept", "application/rss+xml, application/atom+xml, application/xml")
            require(conn.responseCode in 200..299) { "HTTP ${conn.responseCode}" }
            val out = java.io.ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val block = ByteArray(8192)
                while (true) {
                    val n = input.read(block)
                    if (n < 0) break
                    require(out.size() + n <= 1_000_000) { "Feed is too large" }
                    out.write(block, 0, n)
                }
            }
            return NewsFeedParser.parse(out.toString(StandardCharsets.UTF_8.name()))
        } finally {
            conn.disconnect()
        }
    }

    private fun render() {
        entries.removeAllViews()
        val tags = followedTags()
        val visible = if (onlyFollowing) items.filter { FollowedTagPolicy.matches(it.tags, tags) }
            else items
        message.text = "${visible.size} articles shown · ${tags.size} followed tags" +
            if (onlyFollowing) " · followed only" else ""
        val pad = (12 * resources.displayMetrics.density).toInt()
        visible.forEach { entry ->
            val label = TextView(this).apply {
                text = entry.title + (if (entry.date.isNotBlank()) "\n${entry.date}" else "") +
                    (if (entry.tags.isEmpty()) "" else "\n${entry.tags.joinToString(", ")}")
                textSize = 16f
                setPadding(pad, pad, pad, pad)
                setOnClickListener {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(entry.link))
                    runCatching { startActivity(intent) }.onFailure { message.text = "No browser available" }
                }
            }
            entries.addView(label, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2))
            entries.addView(android.view.View(this).apply {
                setBackgroundColor(0x33666666)
            }, LinearLayout.LayoutParams(-1, (resources.displayMetrics.density).toInt().coerceAtLeast(1)))
        }
    }
}
