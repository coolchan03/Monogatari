package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.R
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.headerCard
import org.koitharu.kotatsu.core.ui.image.GeneratedCoverDrawable
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import javax.inject.Inject

/**
 * In-app RSS video/audio playback with optional HTTPS subtitle tracks and
 * Podcasting 2.0 chapter seeking. Does not scrape gated content.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class MediaFeedPlayerActivity : AppCompatActivity() {

    @Inject lateinit var feeds: MediaFeedRepository
    private lateinit var playerView: PlayerView
    private lateinit var status: TextView
    private var player: ExoPlayer? = null
    private var url: String? = null
    private var chaptersUrl: String? = null
    private var transcriptUrl: String? = null
    private var pageUrl: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val stream = intent.getStringExtra(EXTRA_MEDIA_URL)
            ?.takeIf(MediaFeedPlaybackSpec::validStream) ?: run {
            finish()
            return
        }
        url = stream
        val episodeTitle = intent.getStringExtra(EXTRA_TITLE)?.take(200).orEmpty()
            .ifBlank { "Podcast or video episode" }
        title = episodeTitle
        chaptersUrl = intent.getStringExtra(EXTRA_CHAPTERS_URL)
            ?.takeIf(MediaFeedPlaybackSpec::validStream)
        transcriptUrl = intent.getStringExtra(EXTRA_TRANSCRIPT_URL)
            ?.takeIf(MediaFeedPlaybackSpec::validStream)
        pageUrl = intent.getStringExtra(EXTRA_PAGE_URL)
            ?.takeIf(MediaFeedPlaybackSpec::validStream)
        val isAudio = intent.getStringExtra(EXTRA_KIND) != MediaFeedParser.Kind.VIDEO.name

        val dp = resources.displayMetrics.density
        val pad = (12 * dp).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        playerView = PlayerView(this).apply {
            useController = true
            keepScreenOn = false
            contentDescription = "Playback controls for $episodeTitle"
            if (isAudio) setDefaultArtwork(
                GeneratedCoverDrawable(episodeTitle, mediaKind = MediaType.AUDIO)
            )
        }
        root.addView(playerView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        ))
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun addAction(label: String, handler: () -> Unit) {
            // Assist chips, as on the feed and Discover screens.
            buttons.addView(com.google.android.material.chip.Chip(this).apply {
                text = label
                setOnClickListener { handler() }
            }, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = (8 * dp).toInt() })
        }
        if (chaptersUrl != null) addAction(getString(R.string.mono_player_chapters)) { showChapters() }
        if (transcriptUrl != null) addAction(getString(R.string.mono_player_transcript)) { openOfficialUrl(transcriptUrl) }
        if (pageUrl != null) addAction(getString(R.string.mono_player_website)) { openOfficialUrl(pageUrl) }
        if (buttons.childCount > 0) root.addView(HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
            addView(buttons)
        })
        // Compact Kitsune card under the player, in the audio or video form, holding the status line.
        val (statusCard, statusView) = headerCard(
            if (isAudio) MascotForm.AUDIO else MascotForm.VIDEO,
            null,
            if (isAudio) "Audio · resume and seek in the player above"
                else "Video · captions are available when supplied by the feed",
            mascotSizeDp = 40,
        )
        status = statusView.apply { accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE }
        root.addView(statusCard, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = pad })
        setMonogatariContent(root, MonogatariChrome.LIST_MAX_WIDTH_DP)

        val start = savedInstanceState?.getLong(KEY_POSITION)
            ?: AudioResumeStore.read(this, stream)
        val play = savedInstanceState?.getBoolean(KEY_PLAYING) ?: true
        val exo = ExoPlayer.Builder(this).build()
        player = exo
        playerView.player = exo
        val subtitle = transcriptUrl?.let { sidecar ->
            MediaFeedPlaybackSpec.subtitleMimeType(sidecar)?.let { mime ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(sidecar))
                    .setMimeType(mime)
                    .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                    .setLabel("Feed captions")
                    .build()
            }
        }
        val item = MediaItem.Builder()
            .setUri(stream)
            .setMimeType(intent.getStringExtra(EXTRA_MIME_TYPE))
            .setMediaMetadata(MediaMetadata.Builder().setTitle(episodeTitle).build())
            .setSubtitleConfigurations(listOfNotNull(subtitle))
            .build()
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playerView.keepScreenOn = !isAudio && isPlaying
            }
            override fun onPlayerError(error: PlaybackException) {
                status.text = getString(R.string.mono_player_playback_unavailable) +
                    (error.message ?: "Try the official website").take(140)
            }
        })
        exo.setMediaItem(item, start.coerceAtLeast(0L))
        exo.prepare()
        exo.playWhenReady = play
    }

    private fun showChapters() {
        val chaptersLink = chaptersUrl ?: return
        status.text = getString(R.string.mono_player_loading_chapters)
        lifecycleScope.launch {
            try {
                val chapters = feeds.readChapters(chaptersLink)
                if (chapters.isEmpty()) {
                    status.text = getString(R.string.mono_player_no_chapters_were_listed_for_this)
                    return@launch
                }
                status.text = "${chapters.size} chapters"
                MaterialAlertDialogBuilder(this@MediaFeedPlayerActivity)
                    .setTitle(getString(R.string.mono_player_jump_to_chapter))
                    .setItems(chapters.map {
                        "${MediaFeedParser.formatTimestamp(it.startSeconds)}  ${it.title}"
                    }.toTypedArray()) { _, selected ->
                        val destination = MediaFeedPlaybackSpec.chapterPositionMs(
                            chapters[selected].startSeconds
                        )
                        val duration = player?.duration?.takeIf { it > 0 }
                        player?.seekTo(if (duration == null) destination else destination.coerceAtMost(duration))
                        player?.play()
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (failure: Exception) {
                status.text = getString(R.string.mono_player_chapters_unavailable) +
                    failure.message.orEmpty().take(140)
            }
        }
    }

    private fun openOfficialUrl(candidate: String?) {
        val safeUrl = candidate?.takeIf(MediaFeedPlaybackSpec::validStream) ?: return
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(safeUrl))) }
            .onFailure { status.text = getString(R.string.mono_player_no_browser_is_available_for_this) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong(KEY_POSITION, player?.currentPosition?.coerceAtLeast(0) ?: 0L)
        outState.putBoolean(KEY_PLAYING, player?.playWhenReady ?: false)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        url?.let { link ->
            player?.currentPosition?.takeIf { it >= 0 }?.let {
                AudioResumeStore.write(this, link, it)
            }
        }
        player?.pause()
        super.onStop()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEDIA_URL = "media_feed.url"
        const val EXTRA_MIME_TYPE = "media_feed.mime"
        const val EXTRA_TITLE = "media_feed.title"
        const val EXTRA_KIND = "media_feed.kind"
        const val EXTRA_CHAPTERS_URL = "media_feed.chapters"
        const val EXTRA_TRANSCRIPT_URL = "media_feed.transcript"
        const val EXTRA_PAGE_URL = "media_feed.page"
        private const val KEY_POSITION = "playback_position"
        private const val KEY_PLAYING = "playback_started"
    }
}
