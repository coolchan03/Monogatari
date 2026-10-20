package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.progressindicator.LinearProgressIndicator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import javax.inject.Inject

/** User-controlled entertainment podcast / video RSS subscriptions. No background polling. */
@AndroidEntryPoint
class MediaFeedActivity : AppCompatActivity() {
    @Inject lateinit var repository: MediaFeedRepository
    @Inject lateinit var imageLoader: ImageLoader
    private val results = CatalogResultsAdapter()
    private val saved by lazy { getSharedPreferences("monogatari_entertainment_feeds", MODE_PRIVATE) }
    private lateinit var status: TextView
    private lateinit var loading: LinearProgressIndicator
    private lateinit var retryButton: MaterialButton
    private lateinit var feedChips: ChipGroup
    private lateinit var kindChips: ChipGroup
    private var currentUrl: String? = null
    private var entries = emptyList<MediaFeedParser.Episode>()
    private var filter: MediaFeedParser.Kind? = null
    private var loadJob: Job? = null
    private var playlistOnly = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        filter = (savedInstanceState?.getString("kind")
            ?: intent.getStringExtra(EXTRA_MEDIA_TYPE))?.let {
            MediaFeedParser.Kind.entries.firstOrNull { kind -> kind.name == it }
        }
        currentUrl = savedInstanceState?.getString("feed")
        playlistOnly = savedInstanceState?.getBoolean("playlist_only") == true
        title = getString(R.string.screen_title_media_feeds)
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = getString(R.string.mono_feeds_podcasts_audio_drama_and_video_shows)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
            setPadding(0, 0, 0, pad / 2)
        })
        installIncludedFeedsOnce()
        // Same chip language as search filters: one chip per feed, then the playlist.
        feedChips = ChipGroup(this).apply { isSingleSelection = true; isSingleLine = true }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(feedChips)
        })
        kindChips = ChipGroup(this).apply { isSingleSelection = true; isSelectionRequired = true }
        listOf<Pair<String, MediaFeedParser.Kind?>>("All media" to null, "Video" to MediaFeedParser.Kind.VIDEO,
            "Audio" to MediaFeedParser.Kind.AUDIO).forEach { (label, kind) ->
            kindChips.addView(Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                text = label
                isCheckable = true
                isChecked = filter == kind
                setOnClickListener { filter = kind; render() }
            })
        }
        root.addView(kindChips)
        status = TextView(this).apply {
            text = getString(R.string.mono_feeds_add_a_feed_url_to_discover)
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(status)
        loading = LinearProgressIndicator(this).apply {
            isIndeterminate = true
            isVisible = false
            contentDescription = getString(R.string.media_feed_loading)
        }
        root.addView(loading, LinearLayout.LayoutParams(-1, -2))
        retryButton = MaterialButton(this).apply {
            setText(R.string.media_feed_retry)
            isVisible = false
            setOnClickListener { currentUrl?.let(::load) ?: chooseFeed() }
        }
        root.addView(retryButton)
        root.addView(RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MediaFeedActivity,
                MonogatariChrome.columns(context))
            adapter = results
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        results.imageLoader = imageLoader
        setMonogatariContent(root, MonogatariChrome.LIST_MAX_WIDTH_DP)
        val choices = subscribed()
        if (currentUrl == null) currentUrl = INCLUDED_FEEDS.map { it.second }.firstOrNull { it in choices } ?: choices.minOrNull()
        if (playlistOnly) { entries = playlist(); render() }
        else currentUrl?.let(::load) ?: render()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_ADD, 0, "Add feed")
        menu.add(0, MENU_INCLUDED, 1, "Included feeds")
        menu.add(0, MENU_REFRESH, 2, "Refresh")
        menu.add(0, MENU_REMOVE, 3, "Remove a feed")
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            MENU_ADD -> addFeed()
            MENU_INCLUDED -> chooseSuggestedFeed()
            MENU_REFRESH -> currentUrl?.let(::load) ?: chooseFeed()
            MENU_REMOVE -> chooseFeed()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    /** First open: subscribe to the included feeds so the screen is never empty. Removed feeds stay removed. */
    private fun installIncludedFeedsOnce() {
        if (saved.getBoolean(KEY_DEFAULTS_INSTALLED, false)) return
        saved.edit()
            .putStringSet("urls", subscribed() + INCLUDED_FEEDS.map { it.second })
            .putBoolean(KEY_DEFAULTS_INSTALLED, true)
            .apply()
    }

    private fun feedLabel(url: String): String = INCLUDED_FEEDS.firstOrNull { it.second == url }?.first?.substringBefore(" ·")
        ?: runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: url

    private fun renderFeedChips() {
        feedChips.removeAllViews()
        val feeds = subscribed().sortedBy { feedLabel(it).lowercase() }
        for (url in feeds) {
            feedChips.addView(Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
                text = feedLabel(url)
                isCheckable = true
                isChecked = !playlistOnly && url == currentUrl
                setOnClickListener { load(url) }
            })
        }
        feedChips.addView(Chip(this, null, com.google.android.material.R.attr.chipStyle).apply {
            text = getString(R.string.mono_feeds_my_playlist)
            isCheckable = true
            isChecked = playlistOnly
            setOnClickListener {
                playlistOnly = !playlistOnly
                if (playlistOnly) {
                    loadJob?.cancel()
                    entries = playlist()
                    render()
                } else {
                    currentUrl?.let(::load) ?: run { entries = emptyList(); render() }
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("feed", currentUrl)
        outState.putString("kind", filter?.name)
        outState.putBoolean("playlist_only", playlistOnly)
        super.onSaveInstanceState(outState)
    }

    private fun subscribed(): Set<String> = saved.getStringSet("urls", emptySet()).orEmpty().toSet()
    private fun playlist(): List<MediaFeedParser.Episode> =
        MediaFeedPlaylist.decode(saved.getString("playlist_episodes", null))

    private fun togglePlaylist(entry: MediaFeedParser.Episode) {
        val existing = playlist()
        val alreadySaved = MediaFeedPlaylist.contains(existing, entry)
        val next = if (alreadySaved) MediaFeedPlaylist.remove(existing, entry)
            else MediaFeedPlaylist.add(existing, entry)
        saved.edit().putString("playlist_episodes", MediaFeedPlaylist.encode(next)).apply()
        if (playlistOnly) { entries = next; render() }
        else status.text = if (alreadySaved) "Episode removed from playlist."
            else "Episode saved to your playlist."
    }
    /** Last successfully loaded episodes of [url], so the screen opens with something to play. */
    private fun cacheKey(url: String): String = "cache_v1:" + Integer.toHexString(url.hashCode())

    private fun persist(url: String) {
        saved.edit().putStringSet("urls", subscribed() + url).apply()
    }
    private fun addFeed() {
        val input = EditText(this).apply {
            hint = getString(R.string.mono_feeds_https_example_com_podcast_xml)
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.mono_feeds_subscribe_to_an_entertainment_feed))
            .setView(input).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(getString(R.string.mono_feeds_subscribe)) { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                if (!MediaFeedRepository.validUrl(url)) {
                    status.text = getString(R.string.mono_feeds_enter_an_https_rss_or_atom)
                } else load(url, subscribe = true)
            }.show()
    }
    private fun chooseSuggestedFeed() {
        val presets = INCLUDED_FEEDS
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.mono_feeds_included_entertainment_feeds))
            .setItems(presets.map { it.first }.toTypedArray()) { _, index ->
                load(presets[index].second, subscribe = true)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseFeed() {
        val options = subscribed().sorted()
        if (options.isEmpty()) { status.text = getString(R.string.mono_feeds_no_feeds_saved_yet_use_add); return }
        MaterialAlertDialogBuilder(this).setTitle(getString(R.string.mono_feeds_subscribed_feeds))
            .setItems(options.toTypedArray()) { _, i -> load(options[i]) }
            .setNeutralButton(getString(R.string.mono_feeds_remove_feed)) { _, _ ->
                MaterialAlertDialogBuilder(this).setTitle(getString(R.string.mono_feeds_unsubscribe))
                    .setItems(options.toTypedArray()) { _, i ->
                        saved.edit().putStringSet("urls", subscribed() - options[i]).apply()
                        renderFeedChips()
                        if (currentUrl == options[i]) {
                            loadJob?.cancel()
                            currentUrl = null
                            entries = emptyList()
                            render()
                        }
                    }.show()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }
    private fun load(url: String, subscribe: Boolean = false) {
        playlistOnly = false
        loadJob?.cancel()
        retryButton.isVisible = false
        loading.isVisible = true
        // Show the episodes from the last successful load at once; the refresh below replaces them.
        val cached = MediaFeedPlaylist.decode(saved.getString(cacheKey(url), null))
        if (cached.isNotEmpty()) {
            currentUrl = url
            entries = cached
            render()
        }
        status.text = getString(R.string.mono_feeds_loading_feed)
        loadJob = lifecycleScope.launch {
            try {
                val fetched = repository.read(url)
                currentUrl = url
                entries = fetched
                if (subscribe) persist(url)
                if (fetched.isNotEmpty()) {
                    saved.edit().putString(cacheKey(url), MediaFeedPlaylist.encode(fetched.take(MAX_CACHED_EPISODES))).apply()
                }
                render()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                currentUrl = url
                retryButton.isVisible = true
                if (cached.isEmpty()) {
                    entries = emptyList()
                    render()
                }
                val reason = error.message.orEmpty().take(140)
                status.text = if (cached.isNotEmpty()) getString(R.string.mono_feeds_showing_saved_episodes, reason)
                    else "Feed unavailable: $reason"
            } finally {
                loading.isVisible = false
            }
        }
    }
    private fun render() {
        renderFeedChips()
        val visible = entries.filter { filter == null || it.kind == filter }
        status.text = if (playlistOnly) "${visible.size} saved episodes · select an episode to manage it"
            else if (currentUrl == null) "Pick a feed above, or add your own from the menu."
            else "${visible.size} episodes · ${subscribed().size} subscriptions"
        results.show(visible.map { episode ->
            CatalogRow(
                title = episode.title,
                subtitle = listOfNotNull(episode.kind.name.lowercase().replaceFirstChar { it.uppercaseChar() },
                    episode.durationSeconds?.let(MediaFeedParser::formatDuration),
                    if (episode.chaptersUrl != null) "chapters" else null,
                    if (episode.transcriptUrl != null) "transcript" else null,
                    episode.published.takeIf { it.isNotBlank() }).joinToString(" · "),
                coverUrl = episode.coverUrl,
                mediaKind = if (episode.kind == MediaFeedParser.Kind.VIDEO)
                    MediaType.VIDEO else MediaType.AUDIO,
                onOpen = { openEpisode(episode) },
                onLongPress = { openEpisode(episode) },
                actionLabel = if (MediaFeedPlaybackSpec.validStream(episode.mediaUrl))
                    "Play" else null,
                onAction = if (MediaFeedPlaybackSpec.validStream(episode.mediaUrl))
                    ({ playEpisode(episode) }) else null,
            )
        })
    }
    private fun playEpisode(entry: MediaFeedParser.Episode) {
        val media = entry.mediaUrl?.takeIf(MediaFeedPlaybackSpec::validStream) ?: run {
            status.text = getString(R.string.mono_feeds_this_episode_does_not_provide_a)
            return
        }
        startActivity(Intent(this, MediaFeedPlayerActivity::class.java)
            .putExtra(MediaFeedPlayerActivity.EXTRA_MEDIA_URL, media)
            .putExtra(MediaFeedPlayerActivity.EXTRA_MIME_TYPE, entry.mimeType)
            .putExtra(MediaFeedPlayerActivity.EXTRA_TITLE, entry.title)
            .putExtra(MediaFeedPlayerActivity.EXTRA_KIND, entry.kind.name)
            .putExtra(MediaFeedPlayerActivity.EXTRA_CHAPTERS_URL, entry.chaptersUrl)
            .putExtra(MediaFeedPlayerActivity.EXTRA_TRANSCRIPT_URL, entry.transcriptUrl)
            .putExtra(MediaFeedPlayerActivity.EXTRA_PAGE_URL, entry.pageUrl))
    }

    private fun openEpisode(entry: MediaFeedParser.Episode) {
        val media = entry.mediaUrl?.takeIf(MediaFeedPlaybackSpec::validStream)
        val options = buildList {
            if (media != null) {
                add("Play in Monogatari")
                add("Open in another player")
            }
            add(if (MediaFeedPlaylist.contains(playlist(), entry))
                "Remove from playlist" else "Save to playlist")
            if (entry.chaptersUrl != null) add("Show chapters")
            if (entry.transcriptUrl != null) add("View transcript or subtitles")
            if (entry.pageUrl != null) add("Open show website")
        }.toTypedArray()
        MaterialAlertDialogBuilder(this).setTitle(entry.title)
            .setItems(options) { _, index ->
                when (options[index]) {
                    "Save to playlist", "Remove from playlist" -> togglePlaylist(entry)
                    "Show chapters" -> showChapters(entry)
                    "View transcript or subtitles" -> openPage(entry.transcriptUrl)
                    "Open show website" -> openPage(entry.pageUrl)
                    "Play in Monogatari" -> playEpisode(entry)
                    "Open in another player" -> if (media != null) {
                        runCatching {
                            startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(
                                Uri.parse(media), entry.mimeType ?: when (entry.kind) {
                                    MediaFeedParser.Kind.VIDEO -> "video/*"
                                    MediaFeedParser.Kind.AUDIO -> "audio/*"
                                }
                            ))
                        }.onFailure { status.text = getString(R.string.mono_feeds_no_compatible_media_player_available) }
                    }
                }
            }.show()
    }
    private fun showChapters(entry: MediaFeedParser.Episode) {
        val url = entry.chaptersUrl ?: return
        status.text = getString(R.string.mono_feeds_loading_chapters)
        lifecycleScope.launch {
            try {
                val chapters = repository.readChapters(url)
                if (chapters.isEmpty()) { status.text = getString(R.string.mono_feeds_this_episode_lists_no_chapters); return@launch }
                status.text = ""
                MaterialAlertDialogBuilder(this@MediaFeedActivity).setTitle(getString(R.string.mono_feeds_chapters))
                    .setItems(chapters.map { "${MediaFeedParser.formatTimestamp(it.startSeconds)}  ${it.title}" }.toTypedArray(), null)
                    .setPositiveButton(android.R.string.ok, null).show()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                status.text = "Chapters unavailable: ${error.message.orEmpty().take(120)}"
            }
        }
    }
    private fun openPage(link: String?) {
        if (link?.startsWith("https://") == true) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
                .onFailure { status.text = getString(R.string.mono_feeds_no_browser_available) }
        } else status.text = getString(R.string.mono_feeds_this_episode_has_no_public_https)
    }
    companion object {
        const val EXTRA_MEDIA_TYPE = "media_feed_type"
        private const val KEY_DEFAULTS_INSTALLED = "included_feeds_v1"
        private const val MENU_ADD = 8101
        private const val MENU_INCLUDED = 8102
        private const val MENU_REFRESH = 8103
        private const val MENU_REMOVE = 8104
        private const val MAX_CACHED_EPISODES = 40

        /** Pre-installed on first open. The first three returned HTTP 200 in the 2026-10-15 provider check. */
        internal val INCLUDED_FEEDS = listOf(
            "Escape Pod · science fiction stories" to "https://escapepod.org/feed/podcast/",
            "Welcome to Night Vale · fiction podcast" to "https://feeds.megaphone.fm/SBP4591212513",
            "Pop Culture Happy Hour · films and entertainment" to "https://feeds.npr.org/510282/podcast.xml",
            "PodCastle · fantasy stories" to "https://podcastle.org/feed/podcast/",
            "Pseudopod · horror stories" to "https://pseudopod.org/feed/podcast/",
        )
    }
}
