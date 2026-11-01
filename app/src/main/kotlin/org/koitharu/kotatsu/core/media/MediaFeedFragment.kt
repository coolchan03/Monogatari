package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.emptyState
import org.koitharu.kotatsu.core.ui.headerCard
import org.koitharu.kotatsu.core.ui.primaryButton
import org.koitharu.kotatsu.core.ui.statusText
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import javax.inject.Inject

/**
 * User-controlled entertainment podcast / video RSS subscriptions. No background polling.
 * Shown as a main navigation tab, or inside [MediaFeedActivity] when opened from elsewhere
 * (its [MediaFeedActivity.EXTRA_MEDIA_TYPE] extra arrives here as an argument).
 */
@AndroidEntryPoint
class MediaFeedFragment : Fragment() {
	@Inject lateinit var repository: MediaFeedRepository
	@Inject lateinit var imageLoader: ImageLoader
	private val results = CatalogResultsAdapter()
	private val saved by lazy { requireContext().getSharedPreferences("monogatari_entertainment_feeds", Context.MODE_PRIVATE) }
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
	private lateinit var emptyHost: LinearLayout
	private var loadError: String? = null
	private val isBottomTab: Boolean
		get() = arguments?.getBoolean(AppRouter.KEY_IS_BOTTOMTAB, false) == true

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val context = requireContext()
		filter = (savedInstanceState?.getString("kind")
			?: arguments?.getString(MediaFeedActivity.EXTRA_MEDIA_TYPE))?.let {
			MediaFeedParser.Kind.entries.firstOrNull { kind -> kind.name == it }
		}
		currentUrl = savedInstanceState?.getString("feed")
		playlistOnly = savedInstanceState?.getBoolean("playlist_only") == true
		val dp = resources.displayMetrics.density
		val pad = (16 * dp).toInt()
		val root = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad / 2, pad, pad)
		}
		// Compact Kitsune header card, like the other Monogatari media screens.
		root.addView(context.headerCard(MascotForm.AUDIO, null, getString(R.string.mono_feeds_podcasts_audio_drama_and_video_shows), mascotSizeDp = 40).first,
			LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pad / 2 })
		installIncludedFeedsOnce()
		// Same chip language as search filters: one chip per feed, then the playlist.
		feedChips = ChipGroup(context).apply { isSingleSelection = true; isSingleLine = true }
		root.addView(HorizontalScrollView(context).apply {
			isHorizontalScrollBarEnabled = false
			addView(feedChips)
		})
		kindChips = ChipGroup(context).apply { isSingleSelection = true; isSelectionRequired = true }
		listOf<Pair<String, MediaFeedParser.Kind?>>("All media" to null, "Video" to MediaFeedParser.Kind.VIDEO,
			"Audio" to MediaFeedParser.Kind.AUDIO).forEach { (label, kind) ->
			kindChips.addView(Chip(context, null, com.google.android.material.R.attr.chipStyle).apply {
				text = label
				isCheckable = true
				isChecked = filter == kind
				setOnClickListener { filter = kind; render() }
			})
		}
		root.addView(kindChips)
		status = context.statusText(getString(R.string.mono_feeds_add_a_feed_url_to_discover)).apply {
			setPadding(0, pad / 2, 0, pad / 2)
		}
		root.addView(status)
		loading = LinearProgressIndicator(context).apply {
			isIndeterminate = true
			isVisible = false
			contentDescription = getString(R.string.media_feed_loading)
		}
		root.addView(loading, LinearLayout.LayoutParams(-1, -2))
		retryButton = context.primaryButton(getString(R.string.media_feed_retry)) {
			currentUrl?.let(::load) ?: chooseFeed()
		}.apply {
			isVisible = false
		}
		root.addView(retryButton)
		emptyHost = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
		root.addView(emptyHost, LinearLayout.LayoutParams(-1, -2))
		root.addView(RecyclerView(context).apply {
			layoutManager = GridLayoutManager(context,
				MonogatariChrome.columns(context))
			adapter = results
			if (isBottomTab) {
				// Room for the floating navigation bar, which is drawn over the main screen's content.
				clipToPadding = false
				setPadding(0, 0, 0, (96 * dp).toInt())
			}
		}, LinearLayout.LayoutParams(-1, 0, 1f))
		results.imageLoader = imageLoader
		return root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		addMenuProvider(MediaFeedMenuProvider())
		val choices = subscribed()
		if (currentUrl == null) currentUrl = INCLUDED_FEEDS.map { it.second }.firstOrNull { it in choices } ?: choices.minOrNull()
		if (playlistOnly) { entries = playlist(); render() }
		else currentUrl?.let(::load) ?: render()
	}

	private inner class MediaFeedMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menu.add(0, MENU_ADD, 0, "Add feed")
			menu.add(0, MENU_INCLUDED, 1, "Included feeds")
			menu.add(0, MENU_REFRESH, 2, "Refresh")
			menu.add(0, MENU_REMOVE, 3, "Remove a feed")
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			when (menuItem.itemId) {
				MENU_ADD -> addFeed()
				MENU_INCLUDED -> chooseSuggestedFeed()
				MENU_REFRESH -> currentUrl?.let(::load) ?: chooseFeed()
				MENU_REMOVE -> chooseFeed()
				else -> return false
			}
			return true
		}
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
			feedChips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
				text = feedLabel(url)
				isCheckable = true
				isChecked = !playlistOnly && url == currentUrl
				setOnClickListener { load(url) }
			})
		}
		feedChips.addView(Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
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
		val input = EditText(requireContext()).apply {
			hint = getString(R.string.mono_feeds_https_example_com_podcast_xml)
			setSingleLine(true)
		}
		MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.mono_feeds_subscribe_to_an_entertainment_feed))
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
		MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.mono_feeds_included_entertainment_feeds))
			.setItems(presets.map { it.first }.toTypedArray()) { _, index ->
				load(presets[index].second, subscribe = true)
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun chooseFeed() {
		val options = subscribed().sorted()
		if (options.isEmpty()) { status.text = getString(R.string.mono_feeds_no_feeds_saved_yet_use_add); return }
		MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.mono_feeds_subscribed_feeds))
			.setItems(options.toTypedArray()) { _, i -> load(options[i]) }
			.setNeutralButton(getString(R.string.mono_feeds_remove_feed)) { _, _ ->
				MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.mono_feeds_unsubscribe))
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
		loadError = null
		loading.isVisible = true
		// Show the episodes from the last successful load at once; the refresh below replaces them.
		val cached = MediaFeedPlaylist.decode(saved.getString(cacheKey(url), null))
		if (cached.isNotEmpty()) {
			currentUrl = url
			entries = cached
			render()
		}
		status.text = getString(R.string.mono_feeds_loading_feed)
		loadJob = viewLifecycleOwner.lifecycleScope.launch {
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
					loadError = error.message.orEmpty().take(140).ifBlank { getString(R.string.first_run_feed_error_message) }
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
		renderEmptyState(visible.isEmpty())
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
	/** One shared empty state for "no feeds", "empty playlist", "filtered out" and "feed failed". */
	private fun renderEmptyState(isEmpty: Boolean) {
		emptyHost.removeAllViews()
		if (!isEmpty) return
		val ctx = requireContext()
		val error = loadError
		val url = currentUrl
		val retry = getString(R.string.retry)
		emptyHost.addView(when {
			playlistOnly ->
				ctx.emptyState(MascotForm.AUDIO, getString(R.string.first_run_playlist_empty_title),
					getString(R.string.first_run_playlist_empty_message))
			error != null && url != null ->
				ctx.emptyState(MascotForm.SAD, getString(R.string.first_run_feed_error_title), error, retry) { load(url) }
			url == null ->
				ctx.emptyState(MascotForm.AUDIO, getString(R.string.first_run_feeds_none_title),
					getString(R.string.first_run_feeds_none_message), getString(R.string.first_run_add_feed)) { addFeed() }
			entries.isNotEmpty() ->
				ctx.emptyState(MascotForm.SAD, getString(R.string.first_run_feed_filtered_title),
					getString(R.string.first_run_feed_filtered_message))
			else ->
				ctx.emptyState(MascotForm.AUDIO, getString(R.string.first_run_feed_empty_title),
					getString(R.string.first_run_feed_empty_message), retry) { load(url) }
		})
	}
	private fun playEpisode(entry: MediaFeedParser.Episode) {
		val media = entry.mediaUrl?.takeIf(MediaFeedPlaybackSpec::validStream) ?: run {
			status.text = getString(R.string.mono_feeds_this_episode_does_not_provide_a)
			return
		}
		startActivity(Intent(requireContext(), MediaFeedPlayerActivity::class.java)
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
		MaterialAlertDialogBuilder(requireContext()).setTitle(entry.title)
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
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val chapters = repository.readChapters(url)
				if (chapters.isEmpty()) { status.text = getString(R.string.mono_feeds_this_episode_lists_no_chapters); return@launch }
				status.text = ""
				MaterialAlertDialogBuilder(requireContext()).setTitle(getString(R.string.mono_feeds_chapters))
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
	private companion object {
		const val KEY_DEFAULTS_INSTALLED = "included_feeds_v1"
		const val MENU_ADD = 8101
		const val MENU_INCLUDED = 8102
		const val MENU_REFRESH = 8103
		const val MENU_REMOVE = 8104
		const val MAX_CACHED_EPISODES = 40
		val INCLUDED_FEEDS get() = MediaFeedActivity.INCLUDED_FEEDS
	}
}
