package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import com.google.android.material.button.MaterialButton
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
    private lateinit var kindButton: MaterialButton
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
        title = "Entertainment feeds"
        val dp = resources.displayMetrics.density
        val pad = (16 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        root.addView(TextView(this).apply {
            text = "Video & podcast feeds"
            textSize = 24f
        })
        root.addView(TextView(this).apply {
            text = "Subscribe to RSS/Atom feeds for shows, films, fiction podcasts and audio drama."
        })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(controls)
        })
        fun addButton(label: String, action: () -> Unit): MaterialButton =
            MaterialButton(this).apply {
                text = label
                isAllCaps = false
                setOnClickListener { action() }
                controls.addView(this, LinearLayout.LayoutParams(-2, -2).apply {
                    marginEnd = (8 * dp).toInt()
                })
            }
        addButton("Add feed") { addFeed() }
        addButton("My feeds") { chooseFeed() }
        addButton("Suggested feeds") { chooseSuggestedFeed() }
        addButton("Refresh") { currentUrl?.let(::load) ?: chooseFeed() }
        addButton("My playlist") {
            playlistOnly = !playlistOnly
            if (playlistOnly) {
                loadJob?.cancel()
                entries = playlist()
                render()
            } else {
                currentUrl?.let(::load) ?: run { entries = emptyList(); render() }
            }
        }
        kindButton = addButton("All") {
            filter = when (filter) {
                null -> MediaFeedParser.Kind.VIDEO
                MediaFeedParser.Kind.VIDEO -> MediaFeedParser.Kind.AUDIO
                MediaFeedParser.Kind.AUDIO -> null
            }
            render()
        }
        status = TextView(this).apply {
            text = "Add a feed URL to discover podcast or video episodes."
            setPadding(0, pad / 2, 0, pad / 2)
        }
        root.addView(status)
        root.addView(RecyclerView(this).apply {
            layoutManager = GridLayoutManager(this@MediaFeedActivity,
                MonogatariChrome.columns(context))
            adapter = results
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        results.imageLoader = imageLoader
        setMonogatariContent(root, MonogatariChrome.LIST_MAX_WIDTH_DP)
        val choices = subscribed()
        if (currentUrl == null && choices.size == 1) currentUrl = choices.first()
        if (playlistOnly) { entries = playlist(); render() }
        else currentUrl?.let(::load) ?: render()
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
    private fun persist(url: String) {
        saved.edit().putStringSet("urls", subscribed() + url).apply()
    }
    private fun addFeed() {
        val input = EditText(this).apply {
            hint = "https://example.com/podcast.xml"
            setSingleLine(true)
        }
        MaterialAlertDialogBuilder(this).setTitle("Subscribe to an entertainment feed")
            .setView(input).setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton("Subscribe") { _, _ ->
                val url = input.text?.toString()?.trim().orEmpty()
                if (!MediaFeedRepository.validUrl(url)) {
                    status.text = "Enter an HTTPS RSS or Atom feed URL."
                } else load(url, subscribe = true)
            }.show()
    }
    private fun chooseSuggestedFeed() {
        val presets = listOf(
            "Escape Pod · science fiction stories" to "https://escapepod.org/feed/podcast/",
            "Welcome to Night Vale · fiction podcast" to "https://feeds.megaphone.fm/SBP4591212513",
            "Pop Culture Happy Hour · films and entertainment" to "https://feeds.npr.org/510282/podcast.xml",
        )
        MaterialAlertDialogBuilder(this).setTitle("Included entertainment feeds")
            .setItems(presets.map { it.first }.toTypedArray()) { _, index ->
                load(presets[index].second, subscribe = true)
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun chooseFeed() {
        val options = subscribed().sorted()
        if (options.isEmpty()) { status.text = "No feeds saved yet. Choose Add feed."; return }
        MaterialAlertDialogBuilder(this).setTitle("Subscribed feeds")
            .setItems(options.toTypedArray()) { _, i -> load(options[i]) }
            .setNeutralButton("Remove feed") { _, _ ->
                MaterialAlertDialogBuilder(this).setTitle("Unsubscribe")
                    .setItems(options.toTypedArray()) { _, i ->
                        saved.edit().putStringSet("urls", subscribed() - options[i]).apply()
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
        status.text = "Loading feed..."
        loadJob = lifecycleScope.launch {
            try {
                val fetched = repository.read(url)
                currentUrl = url
                entries = fetched
                if (subscribe) persist(url)
                render()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                status.text = "Feed unavailable: ${error.message.orEmpty().take(140)}"
            }
        }
    }
    private fun render() {
        kindButton.text = when (filter) {
            null -> "All media"
            MediaFeedParser.Kind.VIDEO -> "Video"
            MediaFeedParser.Kind.AUDIO -> "Audio"
        }
        val visible = entries.filter { filter == null || it.kind == filter }
        status.text = if (playlistOnly) "${visible.size} saved episodes · select an episode to manage it"
            else if (currentUrl == null) "Add an entertainment podcast or video RSS feed."
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
            status.text = "This episode does not provide a supported HTTPS media stream."
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
                        }.onFailure { status.text = "No compatible media player available." }
                    }
                }
            }.show()
    }
    private fun showChapters(entry: MediaFeedParser.Episode) {
        val url = entry.chaptersUrl ?: return
        status.text = "Loading chapters..."
        lifecycleScope.launch {
            try {
                val chapters = repository.readChapters(url)
                if (chapters.isEmpty()) { status.text = "This episode lists no chapters."; return@launch }
                status.text = ""
                MaterialAlertDialogBuilder(this@MediaFeedActivity).setTitle("Chapters")
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
                .onFailure { status.text = "No browser available." }
        } else status.text = "This episode has no public HTTPS page."
    }
    companion object { const val EXTRA_MEDIA_TYPE = "media_feed_type" }
}
