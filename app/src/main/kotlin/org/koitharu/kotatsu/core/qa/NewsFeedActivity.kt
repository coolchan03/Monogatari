package org.koitharu.kotatsu.core.qa

import org.koitharu.kotatsu.core.util.ExternalLinks
import java.net.URI
import com.google.android.material.chip.ChipGroup
import com.google.android.material.chip.Chip
import android.view.MenuItem
import android.view.Menu
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.core.view.isVisible
import android.widget.HorizontalScrollView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.R
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
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
    private var currentFeed: String = ""
    private lateinit var feedChips: ChipGroup
    private lateinit var searchInput: EditText
    private lateinit var message: TextView
    private lateinit var entries: LinearLayout
    private lateinit var loading: LinearProgressIndicator
    private lateinit var listScroll: ScrollView
    private var restoredScrollY = 0
    private var items = emptyList<NewsFeedParser.Entry>()
    private var onlyFollowing = false
    private var savedOnly = false
    /** Topic chip currently narrowing the list, and the site and order the user picked. */
    private var activeTag: String? = null
    private var sourceFilter: String? = null
    private var oldestFirst = false
    private lateinit var chipsRow: LinearLayout
    private var sourceChip: Chip? = null
    private var feedJob: Job? = null
    private val prefs by lazy { getSharedPreferences("monogatari_news", MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.screen_title_news)
        onlyFollowing = savedInstanceState?.getBoolean("news_following") ?: false
        savedOnly = savedInstanceState?.getBoolean("news_saved") ?: false
        activeTag = savedInstanceState?.getString("news_tag")
        sourceFilter = savedInstanceState?.getString("news_source")
        oldestFirst = savedInstanceState?.getBoolean("news_oldest") ?: false
        restoredScrollY = savedInstanceState?.getInt("news_scroll") ?: 0
        val density = resources.displayMetrics.density
        val padding = (16 * density).toInt()
        val outer = (((resources.configuration.screenWidthDp - 920).coerceAtLeast(0) / 2) * density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding + outer, padding, padding + outer, padding)
        }
        installIncludedFeedsOnce()
        currentFeed = savedInstanceState?.getString("news_feed_url")
            ?: prefs.getString("selected", null) ?: INCLUDED_FEEDS.first().second
        // Feeds and filters use the same chip style as search and the media feeds screen.
        feedChips = ChipGroup(this).apply { isSingleSelection = true; isSingleLine = true }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(feedChips)
        }, LinearLayout.LayoutParams(-1, -2))
        // Highlighted by the Test Center's news checks.
        val filters = ChipGroup(this).apply { isSingleLine = true; id = R.id.monogatari_news_filter }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(filters)
        }, LinearLayout.LayoutParams(-1, -2))
        fun filterChip(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) = Chip(this).apply {
            text = label
            isCheckable = true
            isChecked = checked
            setOnClickListener { onToggle(isChecked); render() }
            filters.addView(this)
        }
        filterChip("Followed topics", onlyFollowing) { onlyFollowing = it }
        filterChip("Saved stories", savedOnly) { savedOnly = it }
        filterChip("Oldest first", oldestFirst) { oldestFirst = it }
        sourceChip = Chip(this).apply {
            setOnClickListener {
                sourceFilter = NewsFilters.nextSource(sourceFilter, NewsFilters.sources(items.map { it.link }))
                render()
            }
            filters.addView(this)
        }
        searchInput = EditText(this).apply {
            hint = "Filter articles by title or topic"
            setSingleLine(true)
            setText(savedInstanceState?.getString("news_search").orEmpty())
            doAfterTextChanged { if (::entries.isInitialized) render() }
        }
        root.addView(searchInput, LinearLayout.LayoutParams(-1, -2))
        chipsRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(chipsRow)
        }, LinearLayout.LayoutParams(-1, -2))
        message = TextView(this).apply { text = "Long-press a story to save it" }
        root.addView(message, LinearLayout.LayoutParams(-1, -2))
        loading = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            isVisible = false
            contentDescription = getString(R.string.news_feed_loading)
        }
        root.addView(loading, LinearLayout.LayoutParams(-1, -2))
        listScroll = ScrollView(this)
        entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        listScroll.addView(entries)
        root.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setMonogatariContent(root)
        renderFeedChips()
        load(currentFeed)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("news_following", onlyFollowing)
        outState.putBoolean("news_saved", savedOnly)
        outState.putString("news_tag", activeTag)
        outState.putString("news_source", sourceFilter)
        outState.putBoolean("news_oldest", oldestFirst)
        outState.putString("news_feed_url", currentFeed)
        outState.putString("news_search", searchInput.text?.toString().orEmpty())
        outState.putInt("news_scroll", listScroll.scrollY)
        super.onSaveInstanceState(outState)
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

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_ADD, 0, "Add feed URL")
        menu.add(0, MENU_INCLUDED, 1, "Included feeds")
        menu.add(0, MENU_REMOVE, 2, "Remove a feed")
        menu.add(0, MENU_TOPICS, 3, "Followed topics")
        menu.add(0, MENU_REFRESH, 4, "Refresh")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_ADD -> askForFeed()
            MENU_INCLUDED -> showSuggestedFeeds()
            MENU_REMOVE -> showSavedFeeds()
            MENU_TOPICS -> editFollowedTopics()
            MENU_REFRESH -> load(currentFeed)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** First open: the included feeds are saved so News is never empty. Feeds the user removes stay removed. */
    private fun installIncludedFeedsOnce() {
        if (prefs.getBoolean(KEY_DEFAULTS_INSTALLED, false)) return
        val feeds = prefs.getStringSet("feeds", emptySet()).orEmpty() + INCLUDED_FEEDS.map { it.second }
        prefs.edit().putStringSet("feeds", feeds).putBoolean(KEY_DEFAULTS_INSTALLED, true).apply()
    }

    private fun feedLabel(url: String): String = INCLUDED_FEEDS.firstOrNull { it.second == url }?.first
        ?: runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

    private fun renderFeedChips() {
        feedChips.removeAllViews()
        for (url in prefs.getStringSet("feeds", emptySet()).orEmpty().sortedBy { feedLabel(it).lowercase() }) {
            feedChips.addView(Chip(this).apply {
                text = feedLabel(url)
                isCheckable = true
                isChecked = url == currentFeed
                setOnClickListener {
                    currentFeed = url
                    prefs.edit().putString("selected", url).apply()
                    renderFeedChips()
                    load(url)
                }
            })
        }
    }

    private fun askForFeed() {
        val input = EditText(this).apply {
            hint = "HTTPS RSS/Atom feed URL"
            setSingleLine(true)
        }
        AlertDialog.Builder(this).setTitle("Add a news feed").setView(input)
            .setPositiveButton("Add") { _, _ -> saveAndLoad(input.text.toString().trim()) }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun editFollowedTopics() {
        val input = EditText(this).apply {
            hint = "Tags to follow, separated by commas"
            setText(followedTags().sorted().joinToString(", "))
        }
        AlertDialog.Builder(this).setTitle("Followed topics").setView(input)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val tags = input.text.toString().split(',')
                    .map { it.trim() }.filter { it.length in 2..50 }.take(40).toSet()
                prefs.edit().putStringSet("tags", tags).apply()
                message.text = "Saved ${tags.size} followed topics on this device"
                render()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showSuggestedFeeds() {
        AlertDialog.Builder(this).setTitle("Built-in entertainment feeds")
            .setItems(INCLUDED_FEEDS.map { it.first }.toTypedArray()) { _, index ->
                saveAndLoad(INCLUDED_FEEDS[index].second)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun saveAndLoad(url: String) {
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
        currentFeed = url
        prefs.edit().putStringSet("feeds", feeds).putString("selected", url).apply()
        renderFeedChips()
        load(url)
    }

    private fun showSavedFeeds() {
        val feeds = prefs.getStringSet("feeds", emptySet()).orEmpty().sorted()
        if (feeds.isEmpty()) { message.text = "No saved feeds"; return }
        AlertDialog.Builder(this).setTitle("Saved feeds")
            .setItems(feeds.map(::feedLabel).toTypedArray()) { _, which ->
                val selected = feeds[which]
                AlertDialog.Builder(this).setTitle(selected)
                    .setPositiveButton("Open") { _, _ ->
                        currentFeed = selected
                        renderFeedChips()
                        load(selected)
                    }
                    .setNegativeButton("Remove") { _, _ ->
                        prefs.edit().putStringSet("feeds", feeds.toMutableSet().apply { remove(selected) }).apply()
                        message.text = "Feed removed"
                        renderFeedChips()
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
        loading.isVisible = true
        feedJob = lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { runCatching { fetchEntries(url) } }
                if (url != currentFeed) return@launch
                result.onSuccess { loaded ->
                    items = loaded
                    render()
                    if (restoredScrollY > 0) {
                        val position = restoredScrollY
                        restoredScrollY = 0
                        listScroll.post { listScroll.scrollTo(0, position) }
                    }
                }.onFailure { error ->
                    message.text = "Feed unavailable: ${error.message.orEmpty().take(140)}"
                }
            } finally {
                loading.isVisible = false
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
        sourceChip?.text = "Site: " + (sourceFilter ?: "all")
        sourceChip?.isChecked = sourceFilter != null
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
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                setOnClickListener {
                    if (!ExternalLinks.open(this@NewsFeedActivity, entry.link)) message.text = "This link can't be opened"
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

    private companion object {
        const val KEY_DEFAULTS_INSTALLED = "included_feeds_v1"
        const val MENU_ADD = 8201
        const val MENU_INCLUDED = 8202
        const val MENU_REMOVE = 8203
        const val MENU_TOPICS = 8204
        const val MENU_REFRESH = 8205

        val INCLUDED_FEEDS = listOf(
            "Anime News Network" to "https://www.animenewsnetwork.com/news/rss.xml",
            "Anime News Network · everything" to "https://www.animenewsnetwork.com/all/rss.xml?ann-edition=us",
            "Anime Expo updates" to "https://www.anime-expo.org/blog/feed/",
            "Standard Ebooks · new releases" to "https://standardebooks.org/feeds/atom/new-releases",
        )
    }
}
