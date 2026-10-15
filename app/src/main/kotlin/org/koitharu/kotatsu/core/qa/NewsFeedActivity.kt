package org.koitharu.kotatsu.core.qa

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.HorizontalScrollView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaAccents
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
import java.text.SimpleDateFormat
import java.util.Locale

/** User-initiated, HTTPS-only RSS/Atom feeds with locally saved followed-tag filters. */
class NewsFeedActivity : AppCompatActivity() {
    private lateinit var feedInput: EditText
    private lateinit var tagInput: EditText
    private lateinit var searchInput: EditText
    private lateinit var message: TextView
    private lateinit var entries: LinearLayout
    private var items = emptyList<NewsFeedParser.Entry>()
    private var onlyFollowing = false
    private var savedOnly = false
    /** Topic chip currently narrowing the list, and the site and order the user picked. */
    private var activeTag: String? = null
    private var sourceFilter: String? = null
    private var oldestFirst = false
    private lateinit var chipsRow: LinearLayout
    private var sourceButton: MaterialButton? = null
    private var orderButton: MaterialButton? = null
    private var feedJob: Job? = null
    private val prefs by lazy { getSharedPreferences("monogatari_news", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "News & followed tags"
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val outer = (((resources.configuration.screenWidthDp - 920).coerceAtLeast(0) / 2) * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding + outer, padding, padding + outer, padding)
        }
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(actions)
        }, LinearLayout.LayoutParams(-1, -2))
        fun action(text: String, onClick: () -> Unit) = MaterialButton(this).apply {
            this.text = text
            isAllCaps = false
            MediaAccents.tintButton(this@NewsFeedActivity, "NEWS", this)
            setOnClickListener { onClick() }
            actions.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                marginEnd = (8 * density).toInt()
            })
        }
        feedInput = EditText(this).apply {
            hint = "HTTPS RSS/Atom feed URL"
            setSingleLine(true)
            setText(prefs.getString("selected", "https://www.animenewsnetwork.com/news/rss.xml"))
        }
        root.addView(feedInput, LinearLayout.LayoutParams(-1, -2))
        action("Save feed and refresh") { saveAndLoad() }
        action("Saved feeds / remove feed") { showSavedFeeds() }
        action("Suggested news feeds") { showSuggestedFeeds() }
        tagInput = EditText(this).apply {
            id = R.id.monogatari_news_filter
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
        action("Saved stories / All") {
            savedOnly = !savedOnly
            render()
        }
        sourceButton = action("Site: all") {
            sourceFilter = NewsFilters.nextSource(sourceFilter, NewsFilters.sources(items.map { it.link }))
            render()
        }
        orderButton = action("Order: newest first") {
            oldestFirst = !oldestFirst
            render()
        }
        searchInput = EditText(this).apply {
            hint = "Filter articles by title or topic"
            setSingleLine(true)
            doAfterTextChanged { if (::entries.isInitialized) render() }
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, -2))
        chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        }, LinearLayout.LayoutParams(-1, -2))
        message = TextView(this).apply { text = "Choose a feed and refresh · long-press a story to save it" }
        root.addView(message, LinearLayout.LayoutParams(-1, -2))
        val listScroll = ScrollView(this)
        entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(entries)
        root.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setMonogatariContent(root)
    }

    private fun followedTags(): Set<String> = prefs.getStringSet("tags", emptySet()).orEmpty().toSet()

    /** Unknown dates remain last, preserving the original feed order among ties. */
    private fun dateKey(value: String): Long {
        for (pattern in listOf("EEE, dd MMM yyyy HH:mm:ss Z", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd")) {
            val parsed = runCatching {
                SimpleDateFormat(pattern, Locale.ENGLISH).apply { isLenient = false }.parse(value)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return Long.MIN_VALUE
    }

    private fun showSuggestedFeeds() {
        val choices = listOf(
            "Anime News Network" to "https://www.animenewsnetwork.com/all/rss.xml?ann-edition=us",
            "Anime Expo updates" to "https://www.anime-expo.org/blog/feed/",
            "Standard Ebooks · new fiction and classics" to "https://standardebooks.org/feeds/atom/new-releases",
        )
        AlertDialog.Builder(this).setTitle("Built-in entertainment feeds")
            .setItems(choices.map { it.first }.toTypedArray()) { _, index ->
                feedInput.setText(choices[index].second)
                saveAndLoad()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

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

    /** One chip per followed topic; tap to narrow the list to it, tap again to clear. */
    private fun renderChips(tags: Set<String>) {
        chipsRow.removeAllViews()
        for (tag in tags.sorted()) {
            chipsRow.addView(com.google.android.material.chip.Chip(this).apply {
                text = tag
                isCheckable = true
                isChecked = tag.equals(activeTag, ignoreCase = true)
                contentDescription = tag
                setOnClickListener {
                    activeTag = if (isChecked) tag else null
                    render()
                }
            })
        }
    }

    private fun render() {
        entries.removeAllViews()
        val tags = followedTags()
        val saved = prefs.getStringSet("saved_stories", emptySet()).orEmpty()
        val query = searchInput.text?.toString()?.trim().orEmpty()
        val visible = items.asSequence()
            .filter { !onlyFollowing || FollowedTagPolicy.matches(it.tags, tags) }
            .filter { !savedOnly || it.link in saved }
            .filter { NewsFilters.matchesTag(it.tags, activeTag) }
            .filter { NewsFilters.matchesSource(it.link, sourceFilter) }
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) ||
                it.tags.any { tag -> tag.contains(query, ignoreCase = true) } }
            .sortedWith(
                if (oldestFirst) compareBy { entry: NewsFeedParser.Entry -> dateKey(entry.date) }
                else compareByDescending { entry: NewsFeedParser.Entry -> dateKey(entry.date) },
            )
            .toList()
        sourceButton?.text = "Site: " + (sourceFilter ?: "all")
        orderButton?.text = if (oldestFirst) "Order: oldest first" else "Order: newest first"
        renderChips(tags)
        message.text = "${visible.size} articles · ${tags.size} followed topics · ${saved.size} saved" +
            if (onlyFollowing) " · followed only" else ""
        val pad = (12 * resources.displayMetrics.density).toInt()
        visible.forEach { entry ->
            val savedNow = entry.link in saved
            val card = MaterialCardView(this).apply {
                radius = 16 * resources.displayMetrics.density
                cardElevation = 0f
                setContentPadding(pad, pad, pad, pad)
            }
            val label = TextView(this).apply {
                text = (if (savedNow) "★  " else "") + entry.title +
                    (if (entry.date.isNotBlank()) "\n${entry.date}" else "") +
                    (if (entry.tags.isEmpty()) "" else "\n${entry.tags.joinToString(" · ")}")
                textSize = 16f
                setOnClickListener {
                    if (entry.link.startsWith("https://") || entry.link.startsWith("http://")) {
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(entry.link))) }
                            .onFailure { message.text = "No browser available" }
                    }
                }
                setOnLongClickListener {
                    val links = prefs.getStringSet("saved_stories", emptySet()).orEmpty().toMutableSet()
                    if (!links.add(entry.link)) links.remove(entry.link)
                    prefs.edit().putStringSet("saved_stories", links).apply()
                    render()
                    true
                }
            }
            card.addView(label)
            entries.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, -2).apply {
                bottomMargin = pad / 2
            })
        }
    }
}
