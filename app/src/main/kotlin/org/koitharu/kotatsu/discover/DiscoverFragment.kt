package org.koitharu.kotatsu.discover

import org.koitharu.kotatsu.discover.taste.TasteProfile
import org.koitharu.kotatsu.discover.reminders.AiringReminderWorker
import org.koitharu.kotatsu.discover.reminders.AiringReminders
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.google.android.material.snackbar.Snackbar
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.core.util.ExternalLinks
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.core.media.BookLibraryActivity
import org.koitharu.kotatsu.core.media.PublicBookCatalog
import android.net.Uri
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.MediaFeedActivity
import org.koitharu.kotatsu.core.media.MediaAccents
import org.koitharu.kotatsu.core.media.UnifiedLibraryActivity
import org.koitharu.kotatsu.core.qa.NewsFeedActivity
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.multi.SearchActivity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.view.View
import androidx.fragment.app.Fragment
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.discover.media.MediaDiscoverActivity
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.databinding.ActivityDiscoverBinding
import org.koitharu.kotatsu.databinding.ItemDiscoverBinding
import javax.inject.Inject

/**
 * AniList trending/popular lists, including Boys' Love; a tap searches every enabled source for the title.
 * Shown as a main navigation tab (like Library or Explore), or inside [DiscoverActivity] when opened from elsewhere.
 */
@AndroidEntryPoint
class DiscoverFragment : Fragment() {

	/** Discover finds what to enjoy; Explore remains the advanced source manager. */
	private enum class MediaSection(val label: String, val description: String) {
		ALL("All", "A mix of what is trending in manga, books, anime and audio, plus picks from your reading"),
		MANGA("Manga & Comics", "Trending comics and Boys' Love titles from AniList"),
		BOOK("Books & Novels", "Light novels, free ebooks and public catalogs with cover art"),
		VIDEO("Anime & Video", "Anime recommendations and public video collections"),
		AUDIO("Audio", "Browse public audiobooks or search installed audio-drama sources"),
		NEWS("News", "Read RSS articles and follow your favorite topics"),
	}

	@Inject
	lateinit var discover: AniListDiscover

	@Inject
	lateinit var recommendations: RecommendationLoader

	@Inject
	lateinit var publicCatalog: PublicBookCatalog

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var history: HistoryRepository

	@Inject
	lateinit var repositories: MangaRepository.Factory

	@Inject
	lateinit var libraryFinder: DiscoverLibraryFinder

	@Inject
	lateinit var airingReminders: AiringReminders

	@Inject
	lateinit var airingReminderScheduler: AiringReminderWorker.Scheduler

	private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
		if (!granted) showSnackbar(getString(R.string.airing_reminder_permission_hint))
	}

	/** v1.3 draft: popular titles re-ranked by tags from the user's own reading history (on device only). */
	private var forYouSelected = false

	private val uiPrefs by lazy { requireContext().getSharedPreferences("monogatari_discover_ui", Context.MODE_PRIVATE) }

	private var _binding: ActivityDiscoverBinding? = null
	private val binding: ActivityDiscoverBinding get() = checkNotNull(_binding)
	private val adapter = ItemsAdapter()
	/** Media type of each card on the All page, so a tap searches the right kind of source. */
	private val itemTypes = HashMap<String, MediaType>()
	private var section = MediaSection.ALL
	private var feed = AniListDiscover.Feed.TRENDING_BL
	private var publicFeedSelected = false
	/** Popular titles straight from built-in video, book and audio sources, so those sections open filled. */
	private var shelfSelected = false
	/** v1.3 draft release calendar: anime episodes airing this week. */
	private var airingSelected = false
	private val shelfOffsets = HashMap<String, Int>()
	private var nextPage = 1
	private var hasNextPage = true
	private var loadJob: Job? = null

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
		ActivityDiscoverBinding.inflate(inflater, container, false).also { _binding = it }.root

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		binding.discoverIntro.setText(R.string.discover_intro_explained)

		// A rotation restores the instance state; a fresh start reopens the chip used last (All on a fresh install).
		val restoredSection = savedInstanceState?.getString(KEY_SECTION)
			?: uiPrefs.getString(PREF_LAST_SECTION, null)
		MediaSection.entries.find { it.name == restoredSection }?.let { section = it }
		savedInstanceState?.getString(KEY_FEED)?.let { name ->
			AniListDiscover.Feed.entries.find { it.name == name }?.let { feed = it }
		}
		publicFeedSelected = savedInstanceState?.getBoolean(KEY_PUBLIC_FEED) ?: false
		shelfSelected = savedInstanceState?.getBoolean(KEY_SHELF_FEED) ?: shelfSources(section).isNotEmpty()
		airingSelected = savedInstanceState?.getBoolean(KEY_AIRING_FEED) ?: false
		forYouSelected = savedInstanceState?.getBoolean(KEY_FOR_YOU_FEED) ?: false

		MediaSection.entries.forEach { media ->
			binding.chipsMedia.addView(Chip(requireContext()).apply {
				id = media.ordinal + 100
				text = media.label
				isCheckable = true
				isChecked = media == section
				if (media != MediaSection.ALL) MediaAccents.tintChip(requireContext(), media.name, this)
			})
		}
		binding.chipsMedia.setOnCheckedStateChangeListener { _, checkedIds ->
			val chosen = checkedIds.firstOrNull()?.let { MediaSection.entries.getOrNull(it - 100) }
			if (chosen != null && chosen != section) {
				section = chosen
				uiPrefs.edit().putString(PREF_LAST_SECTION, chosen.name).apply()
				shelfSelected = shelfSources(section).isNotEmpty()
				publicFeedSelected = false
				forYouSelected = false
				airingSelected = false
				feed = when (section) {
					MediaSection.MANGA -> AniListDiscover.Feed.TRENDING_BL
					MediaSection.BOOK -> AniListDiscover.Feed.LIGHT_NOVELS
					MediaSection.VIDEO -> AniListDiscover.Feed.TRENDING_ANIME
					else -> feed
				}
				showSection()
				reload()
			}
		}
		binding.chipsFeed.setOnCheckedStateChangeListener { _, checkedIds ->
			if (AIRING_FEED_ID in checkedIds) {
				if (!airingSelected) {
					airingSelected = true
					shelfSelected = false
					forYouSelected = false
					publicFeedSelected = false
					reload()
				}
			} else if (SHELF_FEED_ID in checkedIds) {
				if (!shelfSelected) {
					shelfSelected = true
					forYouSelected = false
					publicFeedSelected = false
					airingSelected = false
					reload()
				}
			} else if (FOR_YOU_FEED_ID in checkedIds) {
				if (!forYouSelected) {
					forYouSelected = true
					publicFeedSelected = false
					shelfSelected = false
					airingSelected = false
					reload()
				}
			} else if (PUBLIC_CATALOG_FEED_ID in checkedIds) {
				forYouSelected = false
				shelfSelected = false
				airingSelected = false
				if (!publicFeedSelected) {
					publicFeedSelected = true
					reload()
				}
			} else {
				val selected = checkedIds.firstOrNull()?.let { AniListDiscover.Feed.entries.getOrNull(it - 1) }
				if (selected != null && (selected != feed || publicFeedSelected || forYouSelected || shelfSelected || airingSelected)) {
					publicFeedSelected = false
					forYouSelected = false
					shelfSelected = false
					airingSelected = false
					feed = selected
					reload()
				}
			}
		}
		showSection()

		// Reflow covers from phone portrait to 13-inch tablets and resizable windows.
		val columns = (resources.configuration.screenWidthDp / 176).coerceIn(2, 7)
		val layoutManager = GridLayoutManager(requireContext(), columns)
		binding.recyclerView.layoutManager = layoutManager
		binding.recyclerView.adapter = adapter
		binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 6) {
					loadMore()
				}
			}
		})
		binding.textMessage.setOnClickListener { if (adapter.itemCount == 0) reload() }
		refreshBadges()
		if (adapter.itemCount == 0) reload()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(KEY_FEED, feed.name)
		outState.putString(KEY_SECTION, section.name)
		outState.putBoolean(KEY_PUBLIC_FEED, publicFeedSelected)
		outState.putBoolean(KEY_SHELF_FEED, shelfSelected)
		outState.putBoolean(KEY_AIRING_FEED, airingSelected)
		outState.putBoolean(KEY_FOR_YOU_FEED, forYouSelected)
	}

	override fun onDestroyView() {
		loadJob?.cancel()
		loadJob = null
		binding.recyclerView.adapter = null
		_binding = null
		super.onDestroyView()
	}

	private fun showSection() {
		binding.discoverSectionDescription.text = section.description
        Mascot.bind(binding.discoverMascot, when (section) {
            MediaSection.ALL -> MascotForm.DEFAULT
            MediaSection.MANGA -> MascotForm.MANGA
            MediaSection.BOOK -> MascotForm.BOOK
            MediaSection.VIDEO -> MascotForm.VIDEO
            MediaSection.AUDIO -> MascotForm.AUDIO
            MediaSection.NEWS -> MascotForm.NEWS
        })
		binding.chipsFeed.removeAllViews()
		val options = AniListDiscover.Feed.entries.filter { entry ->
			when (section) {
				MediaSection.MANGA -> entry.type == "MANGA" && entry != AniListDiscover.Feed.LIGHT_NOVELS
				MediaSection.BOOK -> entry == AniListDiscover.Feed.LIGHT_NOVELS
				MediaSection.VIDEO -> entry.type == "ANIME"
				else -> false
			}
		}
		if (options.isNotEmpty() && feed !in options) feed = options.first()
		val publicLabel = when (section) {
			MediaSection.BOOK -> "Public ebooks"
			MediaSection.VIDEO -> "Archive videos"
			MediaSection.AUDIO -> "Public audiobooks"
			else -> null
		}
		val shelf = shelfSources(section)
		if (shelf.isEmpty()) shelfSelected = false
		(binding.chipsFeed.parent as ViewGroup).isVisible = options.isNotEmpty() || publicLabel != null || shelf.isNotEmpty()
		if (shelf.isNotEmpty()) {
			binding.chipsFeed.addView(Chip(requireContext()).apply {
				id = SHELF_FEED_ID
				setText(when (section) {
					MediaSection.VIDEO -> R.string.discover_shelf_video
					MediaSection.AUDIO -> R.string.discover_shelf_audio
					else -> R.string.discover_shelf_books
				})
				isCheckable = true
				isChecked = shelfSelected && !airingSelected
				MediaAccents.tintChip(requireContext(), section.name, this)
			})
		}
		if (section == MediaSection.VIDEO) {
			binding.chipsFeed.addView(Chip(requireContext()).apply {
				id = AIRING_FEED_ID
				setText(R.string.discover_airing_this_week)
				isCheckable = true
				isChecked = airingSelected
				MediaAccents.tintChip(requireContext(), section.name, this)
			})
		} else airingSelected = false
		if (section == MediaSection.MANGA || section == MediaSection.VIDEO) {
			binding.chipsFeed.addView(Chip(requireContext()).apply {
				id = FOR_YOU_FEED_ID
				setText(R.string.discover_for_you)
				isCheckable = true
				isChecked = forYouSelected && !shelfSelected && !airingSelected
				MediaAccents.tintChip(requireContext(), section.name, this)
			})
		} else forYouSelected = false
		options.forEach { entry ->
			binding.chipsFeed.addView(Chip(requireContext()).apply {
				id = entry.ordinal + 1
				setText(entry.titleRes)
				isCheckable = true
				isChecked = entry == feed && !publicFeedSelected && !forYouSelected && !shelfSelected && !airingSelected
				MediaAccents.tintChip(requireContext(), section.name, this)
			})
		}
		publicLabel?.let { label ->
			binding.chipsFeed.addView(Chip(requireContext()).apply {
				id = PUBLIC_CATALOG_FEED_ID
				text = label
				isCheckable = true
				isChecked = publicFeedSelected && !shelfSelected && !airingSelected
				MediaAccents.tintChip(requireContext(), section.name, this)
			})
		}
		binding.discoverActions.removeAllViews()
		fun action(title: String, run: () -> Unit) {
			// Assist chips, like the media and feed rows above, instead of a row of large filled buttons.
			binding.discoverActions.addView(Chip(requireContext()).apply {
				text = title
				isCheckable = false
				MediaAccents.tintChip(requireContext(), section.name, this)
				setOnClickListener { run() }
			}, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
				marginEnd = (8 * resources.displayMetrics.density).toInt()
			})
		}
		when (section) {
			MediaSection.ALL -> {
				action(getString(R.string.discover_all_search)) { router.openMainSearch("ALL") }
				action(getString(R.string.mono_discover_my_library)) { startActivity(Intent(requireContext(), UnifiedLibraryActivity::class.java)) }
			}
			MediaSection.MANGA -> {
				action(getString(R.string.mono_discover_search_manga)) { router.openMainSearch(MediaType.MANGA.name) }
				action(getString(R.string.mono_discover_my_library)) { startActivity(Intent(requireContext(), UnifiedLibraryActivity::class.java)) }
			}
			MediaSection.BOOK -> {
				action(getString(R.string.mono_discover_books_catalog_opds)) { startActivity(Intent(requireContext(), BookLibraryActivity::class.java)) }
				action(getString(R.string.mono_discover_search_novel_sources)) { router.openMainSearch(MediaType.BOOK.name) }
				action(getString(R.string.discover_see_more_books)) {
					startActivity(MediaDiscoverActivity.intent(requireContext(), MediaType.BOOK))
				}
			}
			MediaSection.VIDEO -> {
				action(getString(R.string.mono_discover_search_video_websites)) { router.openMainSearch(MediaType.VIDEO.name) }
				action(getString(R.string.mono_discover_archive_public_videos)) { searchArchiveVideos() }
				action(getString(R.string.mono_discover_video_rss_feeds)) { openMediaFeeds(MediaType.VIDEO) }
				action(getString(R.string.mono_discover_my_saved_videos)) { openLibrary(MediaType.VIDEO) }
				action(getString(R.string.discover_see_more_videos)) {
					startActivity(MediaDiscoverActivity.intent(requireContext(), MediaType.VIDEO))
				}
			}
			MediaSection.AUDIO -> {
				action(getString(R.string.mono_discover_find_audiobooks_dramas)) { router.openMainSearch(MediaType.AUDIO.name) }
				action(getString(R.string.mono_discover_fiction_podcasts_audio_drama)) { openMediaFeeds(MediaType.AUDIO) }
				action(getString(R.string.mono_discover_my_audiobooks)) { openLibrary(MediaType.AUDIO) }
			}
			MediaSection.NEWS -> {
				action(getString(R.string.mono_discover_news_followed_tags)) { startActivity(Intent(requireContext(), NewsFeedActivity::class.java)) }
			}
		}
		// Discover recommends titles; Explore browses the source websites themselves.
		action(getString(R.string.discover_browse_sources)) { openExploreTab() }
	}

	private fun openExploreTab() {
		startActivity(
			Intent(requireContext(), org.koitharu.kotatsu.main.ui.MainActivity::class.java)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
				.putExtra(org.koitharu.kotatsu.main.ui.MainActivity.EXTRA_OPEN_EXPLORE, true),
		)
		// Inside the main screen this switches tabs; inside the stand-alone host it also closes the host.
		(activity as? DiscoverActivity)?.finish()
	}

	private fun openMediaFeeds(type: MediaType) {
		startActivity(Intent(requireContext(), MediaFeedActivity::class.java)
			.putExtra(MediaFeedActivity.EXTRA_MEDIA_TYPE, type.name))
	}

	private fun openLibrary(type: MediaType) {
		startActivity(Intent(requireContext(), UnifiedLibraryActivity::class.java)
			.putExtra(UnifiedLibraryActivity.EXTRA_MEDIA_KIND, type.name))
	}

	private fun searchArchiveVideos() {
		val input = EditText(requireContext()).apply { hint = getString(R.string.mono_discover_search_public_video_archives); setSingleLine(true) }
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(getString(R.string.mono_discover_internet_archive_videos))
			.setView(input)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(getString(R.string.mono_discover_search)) { _, _ ->
				val term = input.text?.toString()?.trim().orEmpty()
				if (term.isBlank()) return@setPositiveButton
				viewLifecycleOwner.lifecycleScope.launch {
					val videos = runCatching { publicCatalog.searchInternetArchiveVideos(term) }
					videos.onSuccess { results ->
						if (results.isEmpty()) {
							showMessage(getString(R.string.mono_discover_no_public_archive_videos_matched_that))
						} else {
							MaterialAlertDialogBuilder(requireContext())
								.setTitle("Internet Archive · ${results.size} results")
								.setItems(results.map { it.title }.toTypedArray()) { _, position ->
									if (!ExternalLinks.open(requireContext(), results[position].url)) {
										showMessage(getString(R.string.link_cannot_open))
									}
								}
								.setNegativeButton(android.R.string.cancel, null).show()
						}
					}.onFailure { showMessage("Archive video search unavailable: ${it.message.orEmpty().take(100)}") }
				}
			}.show()
	}

	private fun openTypedSearch(title: String, type: MediaType) {
		startActivity(Intent(requireContext(), SearchActivity::class.java)
			.putExtra(AppRouter.KEY_QUERY, title)
			.putExtra(AppRouter.KEY_KIND, SearchKind.SIMPLE)
			.putExtra(SearchActivity.EXTRA_MEDIA_TYPE, type.name))
	}

	private fun reload() {
		loadJob?.cancel()
		loadJob = null
		adapter.items = emptyList()
		nextPage = 1
		shelfOffsets.clear()
		itemTypes.clear()
		hasNextPage = section in setOf(MediaSection.MANGA, MediaSection.BOOK, MediaSection.VIDEO) ||
			(section == MediaSection.AUDIO && (publicFeedSelected || shelfSelected))
		if (section == MediaSection.ALL) {
			loadAll()
		} else if (hasNextPage) {
			loadMore()
		} else {
			binding.progress.isVisible = false
			showMessage(when (section) {
				MediaSection.AUDIO -> "Search your installed audiobook and audio-drama sources above."
				MediaSection.NEWS -> "Open RSS and followed tags above to read stories from your favorite feeds."
				else -> ""
			})
		}
	}

	/** The All page: one mixed list of recommendations from every media type, loaded in a single pass. */
	private fun loadAll() {
		binding.progress.isVisible = true
		binding.textMessage.isVisible = false
		loadJob = viewLifecycleOwner.lifecycleScope.launch {
			try {
				val picks = recommendations.load(null, settings.isNsfwContentDisabled)
				if (section != MediaSection.ALL) return@launch
				picks.forEach { itemTypes[it.item.uniqueKey] = it.type }
				adapter.items = picks.map { it.item }
				hasNextPage = false
				if (picks.isEmpty()) showMessage(getString(R.string.nothing_found))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				if (adapter.itemCount == 0) showMessage(getString(R.string.discover_load_failed, e.message.orEmpty()))
			} finally {
				_binding?.progress?.isVisible = false
			}
		}
	}

	private fun loadMore() {
		if (loadJob?.isActive == true || !hasNextPage) return
		val requestedFeed = feed
		val requestedSection = section
		val requestedPublic = publicFeedSelected
		val requestedShelf = shelfSelected
		val requestedAiring = airingSelected
		val requestedForYou = forYouSelected
		val page = nextPage
		binding.progress.isVisible = true
		binding.textMessage.isVisible = false
		loadJob = viewLifecycleOwner.lifecycleScope.launch {
			try {
				val result = when {
					requestedAiring -> discover.loadAiring(page, 7, ::airingLabel)
					requestedShelf -> loadShelf(requestedSection)
					requestedPublic -> loadPublicFeed(requestedSection, page)
					requestedForYou -> loadForYou(requestedFeed, page)
					else -> discover.load(requestedFeed, page)
				}
				if (requestedFeed != feed || requestedSection != section ||
					requestedPublic != publicFeedSelected || requestedShelf != shelfSelected ||
					requestedAiring != airingSelected || requestedForYou != forYouSelected) return@launch
				// Same switch as the media Discover pages: hide adult titles when the user asked for that.
				val known = adapter.items.mapTo(HashSet()) { it.uniqueKey }
				val unseen = result.items.filter { it.uniqueKey !in known }
				val additions = AniListDiscover.visibleItems(unseen, settings.isNsfwContentDisabled)
				adapter.items = adapter.items + additions
				hasNextPage = result.hasNextPage && unseen.isNotEmpty()
				nextPage = page + 1
				if (adapter.itemCount == 0) {
					showMessage(getString(R.string.nothing_found))
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				if (adapter.itemCount == 0) {
					showMessage(getString(R.string.discover_load_failed, e.message.orEmpty()))
				}
			} finally {
				// Also runs after onDestroyView cancelled the load, when the view is already gone.
				_binding?.progress?.isVisible = false
			}
		}
	}

	/** Two pages of the section's popular feed, re-ranked by the user's history tags; falls back to popular order. */
	private suspend fun loadForYou(base: AniListDiscover.Feed, page: Int): AniListDiscover.Page {
		if (page > 1) return AniListDiscover.Page(emptyList(), false)
		val candidates = (discover.load(base, 1).items + runCatchingCancellable { discover.load(base, 2).items }.getOrDefault(emptyList()))
			.distinctBy { it.uniqueKey }
		val read = runCatchingCancellable { history.getList(0, 150) }.getOrDefault(emptyList())
		val profile = TasteProfile.build(read.mapIndexed { index, manga ->
			TasteProfile.Signal(manga.tags.map { it.title }, favourite = false, readChapters = 5, dropped = false, ageDays = index)
		})
		val readTitles = read.mapTo(HashSet()) { it.title.lowercase() }
		val ranked = profile.rank(candidates.map { item ->
			TasteProfile.Candidate(item, item.genres, alreadyInLibrary = item.title.lowercase() in readTitles)
		}, limit = 60)
		val top = profile.topTags(3)
		// Runs on the main dispatcher (viewLifecycleOwner.lifecycleScope), so the view can be touched directly.
		if (_binding != null) {
			showMessage(if (top.isEmpty()) getString(R.string.discover_for_you_empty)
				else getString(R.string.discover_for_you_based_on, top.joinToString(", ")))
		}
		return AniListDiscover.Page(ranked.ifEmpty { candidates }, false)
	}

	/** "Ep 5 · Tue 18:30" in the device's locale and time zone. */
	private fun airingLabel(episode: Int, airingAt: Long): String {
		val time = java.text.SimpleDateFormat("EEE HH:mm", java.util.Locale.getDefault()).format(java.util.Date(airingAt * 1000))
		return if (episode > 0) getString(R.string.discover_airing_episode, episode, time) else time
	}

	/** Built-in sources whose no-query list is a real popular/trending listing that needs no account or key. */
	private fun shelfSources(kind: MediaSection): List<BuiltinSource> = when (kind) {
		MediaSection.VIDEO -> listOf(BuiltinSource.DAILYMOTION, BuiltinSource.ODYSEE, BuiltinSource.IA_VIDEO)
		MediaSection.BOOK -> listOf(BuiltinSource.GUTENBERG, BuiltinSource.ROYAL_ROAD, BuiltinSource.OPEN_LIBRARY)
		MediaSection.AUDIO -> listOf(BuiltinSource.LIBRIVOX, BuiltinSource.IA_AUDIO)
		else -> emptyList()
	}

	/**
	 * The next page of every shelf source, interleaved so no single site fills the screen. A source that fails is
	 * skipped; the page only fails when all of them do.
	 */
	private suspend fun loadShelf(kind: MediaSection): AniListDiscover.Page = coroutineScope {
		val sources = shelfSources(kind)
		val results = sources.map { source ->
			async {
				val offset = shelfOffsets[source.name] ?: 0
				// A timeout counts as a failure of this source only (withTimeout would cancel the whole shelf).
				source to runCatchingCancellable {
					withTimeoutOrNull(25_000) { repositories.create(source).getList(offset, null, null) }
						?: throw java.io.IOException("${source.title} timed out")
				}
			}
		}.awaitAll()
		results.forEach { (source, result) ->
			result.getOrNull()?.let { shelfOffsets[source.name] = (shelfOffsets[source.name] ?: 0) + it.size }
		}
		if (results.all { it.second.isFailure }) {
			throw results.first().second.exceptionOrNull() ?: IllegalStateException()
		}
		val lists = results.map { (source, result) ->
			result.getOrDefault(emptyList()).map { manga ->
				AniListDiscover.Item(
					id = manga.id.hashCode(),
					title = manga.title,
					alternativeTitle = null,
					coverUrl = manga.coverUrl?.takeIf { it.startsWith("https://") },
					score = null,
					isAdult = manga.contentRating == ContentRating.ADULT,
					providerLabel = source.title,
					manga = manga,
				)
			}
		}
		val mixed = ArrayList<AniListDiscover.Item>()
		val longest = lists.maxOfOrNull { it.size } ?: 0
		for (i in 0 until longest) lists.forEach { list -> list.getOrNull(i)?.let { mixed += it } }
		AniListDiscover.Page(mixed, lists.any { it.isNotEmpty() })
	}

	private suspend fun loadPublicFeed(kind: MediaSection, page: Int): AniListDiscover.Page {
		val books = when (kind) {
			MediaSection.BOOK -> publicCatalog.searchGutenberg("", page)
			MediaSection.VIDEO -> publicCatalog.searchInternetArchiveVideos("animation", page)
			MediaSection.AUDIO -> publicCatalog.searchInternetArchiveAudio("audiobook", page)
			else -> emptyList()
		}
		return PublicDiscoverCatalog.asPage(books, if (kind == MediaSection.BOOK) 32 else 25)
	}

	/** Long-press menu of an "Airing this week" card: follow/unfollow reminders, or search the romaji title. */
	/** Long-press on a card: add it to the library, or search its alternative title. */
	private fun showCardMenu(item: AniListDiscover.Item, type: MediaType) {
		val actions = ArrayList<Pair<String, () -> Unit>>()
		actions.add(Pair<String, () -> Unit>(getString(R.string.discover_add_to_library), {
			addDiscoverItemToLibrary(item, type, libraryFinder) { openTypedSearch(it, type) }
		}))
		item.alternativeTitle?.let { alternative ->
			actions.add(Pair<String, () -> Unit>(getString(R.string.discover_search_alternative), { openTypedSearch(alternative, type) }))
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(item.title)
			.setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun showAiringMenu(item: AniListDiscover.Item, mediaId: Int, type: MediaType) {
		val followed = airingReminders.isFollowed(mediaId)
		val actions = ArrayList<Pair<String, () -> Unit>>()
		actions.add(Pair<String, () -> Unit>(getString(R.string.discover_add_to_library), {
			addDiscoverItemToLibrary(item, type, libraryFinder) { openTypedSearch(it, type) }
		}))
		if (followed) {
			actions.add(Pair<String, () -> Unit>(getString(R.string.airing_reminder_unfollow), { unfollowAiring(mediaId, item.title) }))
		} else {
			actions.add(Pair<String, () -> Unit>(getString(R.string.airing_reminder_follow), { followAiring(mediaId, item.title) }))
		}
		item.alternativeTitle?.let { alternative ->
			actions.add(Pair<String, () -> Unit>(getString(R.string.airing_reminder_search_alternative), { openTypedSearch(alternative, type) }))
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(item.title)
			.setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun followAiring(mediaId: Int, title: String) {
		val first = airingReminders.follow(mediaId, title)
		airingReminderScheduler.update(hasFollowed = true)
		refreshBadges()
		val context = requireContext()
		if (!context.checkNotificationPermission(AiringReminderWorker.CHANNEL_ID)) {
			if (first && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
			} else {
				showSnackbar(getString(R.string.airing_reminder_permission_hint))
			}
		} else {
			showSnackbar(getString(R.string.airing_reminder_followed, title))
		}
	}

	private fun unfollowAiring(mediaId: Int, title: String) {
		airingReminders.unfollow(mediaId)
		airingReminderScheduler.update(hasFollowed = airingReminders.followedIds().isNotEmpty())
		refreshBadges()
		showSnackbar(getString(R.string.airing_reminder_unfollowed, title))
	}

	private fun refreshBadges() {
		adapter.followedIds = airingReminders.followedIds()
	}

	private fun showSnackbar(text: String) {
		val view = _binding?.root ?: return
		Snackbar.make(view, text, Snackbar.LENGTH_LONG).show()
	}

	private fun showMessage(text: String) {
		val view = _binding?.textMessage ?: return
		view.text = text
		view.isVisible = true
	}

	private class Holder(val binding: ItemDiscoverBinding) : RecyclerView.ViewHolder(binding.root)

	private inner class ItemsAdapter : RecyclerView.Adapter<Holder>() {

		var items: List<AniListDiscover.Item> = emptyList()
			set(value) {
				field = value
				@Suppress("NotifyDataSetChanged")
				notifyDataSetChanged()
			}

		/** AniList ids of shows with airing reminders; their cards get a bell in the badge. */
		var followedIds: Set<Int> = emptySet()
			set(value) {
				field = value
				@Suppress("NotifyDataSetChanged")
				notifyDataSetChanged()
			}

		override fun getItemCount() = items.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			Holder(ItemDiscoverBinding.inflate(LayoutInflater.from(parent.context), parent, false))

		override fun onBindViewHolder(holder: Holder, position: Int) {
			val item = items[position]
			holder.binding.textTitle.text = item.title
			val bell = item.aniListMediaId?.takeIf { it in followedIds }?.let { getString(R.string.airing_reminder_marker) }
			val label = item.providerLabel?.let { if (bell != null) "$bell $it" else it } ?: bell
			val badge = listOfNotNull(label, item.score?.let { "$it%" }, if (item.isAdult) "18+" else null)
			holder.binding.textScore.isVisible = badge.isNotEmpty()
			holder.binding.textScore.text = badge.joinToString(" · ")
			holder.binding.imageCover.setImageDrawable(null)
			item.coverUrl?.let { url ->
				coil.enqueue(
					ImageRequest.Builder(holder.itemView.context)
						.data(url)
						.crossfade(true)
						.target(holder.binding.imageCover)
						.build(),
				)
			}
			val type = when (section) {
				MediaSection.ALL -> itemTypes[item.uniqueKey] ?: MediaType.MANGA
				MediaSection.BOOK -> MediaType.BOOK
				MediaSection.VIDEO -> MediaType.VIDEO
				else -> MediaType.MANGA
			}
			holder.binding.root.setOnClickListener {
				item.manga?.let { manga ->
					router.openDetails(manga)
					return@setOnClickListener
				}
				val link = item.officialUrl
				if (link != null && link.startsWith("https://")) {
					runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
						.onFailure { showMessage(getString(R.string.mono_discover_no_browser_is_available_for_this)) }
				} else openTypedSearch(item.title, type)
			}
			holder.binding.root.setOnLongClickListener {
				item.aniListMediaId?.let { mediaId ->
					showAiringMenu(item, mediaId, type)
					return@setOnLongClickListener true
				}
				showCardMenu(item, type)
				true
			}
		}
	}

	private companion object {

		const val PREF_LAST_SECTION = "last_section"
		const val KEY_FEED = "feed"
		const val KEY_SECTION = "media_section"
		const val KEY_PUBLIC_FEED = "public_catalog_feed"
		const val PUBLIC_CATALOG_FEED_ID = 11000
		const val FOR_YOU_FEED_ID = 11001
		const val SHELF_FEED_ID = 11002
		const val KEY_SHELF_FEED = "shelf_feed"
		const val AIRING_FEED_ID = 11003
		const val KEY_AIRING_FEED = "airing_feed"
		const val KEY_FOR_YOU_FEED = "for_you_feed"
	}
}
