package org.koitharu.kotatsu.core.qa

import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaFeedRepository
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.TapCounter
import org.koitharu.kotatsu.core.util.ExternalLinks
import org.koitharu.kotatsu.core.util.FeedXml
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import java.net.URI
import javax.inject.Inject

/**
 * User-initiated, HTTPS-only RSS/Atom feeds with locally saved followed-tag filters.
 * The last articles of each feed are kept on the device so the screen opens with stories while it refreshes.
 * Shown as a main navigation tab, or inside [NewsFeedActivity] when opened from elsewhere.
 */
@AndroidEntryPoint
class NewsFeedFragment : Fragment() {
	@Inject lateinit var repository: MediaFeedRepository
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
	private lateinit var emptyState: LinearLayout
	private lateinit var mascot: ImageView
	private lateinit var retryButton: MaterialButton
	private val eggTaps = TapCounter()
	/** Shown instead of the counts when nothing could be loaded or cached. */
	private var errorText: String? = null
	/** Shown above the counts, e.g. when saved articles are displayed after a failed refresh. */
	private var noticeText: String? = null
	private val isBottomTab: Boolean
		get() = arguments?.getBoolean(AppRouter.KEY_IS_BOTTOMTAB, false) == true
	private val prefs by lazy { requireContext().getSharedPreferences("monogatari_news", Context.MODE_PRIVATE) }

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View {
		val context = requireContext()
		onlyFollowing = savedInstanceState?.getBoolean("news_following") ?: false
		savedOnly = savedInstanceState?.getBoolean("news_saved") ?: false
		activeTag = savedInstanceState?.getString("news_tag")
		sourceFilter = savedInstanceState?.getString("news_source")
		oldestFirst = savedInstanceState?.getBoolean("news_oldest") ?: false
		restoredScrollY = savedInstanceState?.getInt("news_scroll") ?: 0
		val density = resources.displayMetrics.density
		val padding = (16 * density).toInt()
		val outer = (((resources.configuration.screenWidthDp - 920).coerceAtLeast(0) / 2) * density).toInt()
		val root = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding + outer, padding, padding + outer, padding)
		}
		installIncludedFeedsOnce()
		currentFeed = savedInstanceState?.getString("news_feed_url")
			?: prefs.getString("selected", null) ?: ALL_FEEDS
		// Feeds and filters use the same chip style as search and the media feeds screen.
		feedChips = ChipGroup(context).apply { isSingleSelection = true; isSingleLine = true }
		root.addView(HorizontalScrollView(context).apply {
			isHorizontalScrollBarEnabled = false
			addView(feedChips)
		}, LinearLayout.LayoutParams(-1, -2))
		// Highlighted by the Test Center's news checks.
		val filters = ChipGroup(context).apply { isSingleLine = true; id = R.id.monogatari_news_filter }
		root.addView(HorizontalScrollView(context).apply {
			isHorizontalScrollBarEnabled = false
			addView(filters)
		}, LinearLayout.LayoutParams(-1, -2))
		fun filterChip(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) = Chip(context).apply {
			text = label
			isCheckable = true
			isChecked = checked
			setOnClickListener { onToggle(isChecked); render() }
			filters.addView(this)
		}
		filterChip(getString(R.string.news_filter_followed), onlyFollowing) { onlyFollowing = it }
		filterChip(getString(R.string.news_filter_saved), savedOnly) { savedOnly = it }
		filterChip(getString(R.string.news_filter_oldest_first), oldestFirst) { oldestFirst = it }
		sourceChip = Chip(context).apply {
			setOnClickListener {
				sourceFilter = NewsFilters.nextSource(sourceFilter, NewsFilters.sources(items.map { it.link }))
				render()
			}
			filters.addView(this)
		}
		searchInput = EditText(context).apply {
			hint = getString(R.string.mono_news_filter_articles_by_title_or_topic)
			setSingleLine(true)
			setText(savedInstanceState?.getString("news_search").orEmpty())
			doAfterTextChanged { if (::entries.isInitialized) render() }
		}
		root.addView(searchInput, LinearLayout.LayoutParams(-1, -2))
		chipsRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
		root.addView(HorizontalScrollView(context).apply {
			isHorizontalScrollBarEnabled = false
			addView(chipsRow)
		}, LinearLayout.LayoutParams(-1, -2))
		message = TextView(context).apply { text = getString(R.string.mono_news_long_press_a_story_to_save) }
		root.addView(message, LinearLayout.LayoutParams(-1, -2))
		loading = LinearProgressIndicator(context).apply {
			isIndeterminate = true
			isVisible = false
			contentDescription = getString(R.string.news_feed_loading)
		}
		root.addView(loading, LinearLayout.LayoutParams(-1, -2))
		// Kitsune keeps an empty or failed feed company, like the library screens. Tap her quickly for a surprise.
		emptyState = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			gravity = Gravity.CENTER_HORIZONTAL
			isVisible = false
		}
		mascot = ImageView(context).apply {
			adjustViewBounds = true
			setOnClickListener { if (eggTaps.tap(android.os.SystemClock.uptimeMillis())) Mascot.showEasterEgg(requireActivity()) }
		}
		emptyState.addView(mascot, LinearLayout.LayoutParams((160 * density).toInt(), -2))
		retryButton = MaterialButton(context).apply {
			setText(R.string.retry)
			isVisible = false
			setOnClickListener { load(currentFeed) }
		}
		emptyState.addView(retryButton, LinearLayout.LayoutParams(-2, -2))
		root.addView(emptyState, LinearLayout.LayoutParams(-1, -2).apply { gravity = Gravity.CENTER_HORIZONTAL })
		listScroll = ScrollView(context)
		if (isBottomTab) {
			// Room for the floating navigation bar, which is drawn over the main screen's content.
			listScroll.clipToPadding = false
			listScroll.setPadding(0, 0, 0, (96 * density).toInt())
		}
		entries = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
		listScroll.addView(entries)
		root.addView(listScroll, LinearLayout.LayoutParams(-1, 0, 1f))
		return root
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		addMenuProvider(NewsMenuProvider())
		renderFeedChips()
		load(currentFeed, fallbackToAll = true)
	}

	override fun onSaveInstanceState(outState: Bundle) {
		if (!::searchInput.isInitialized) {
			super.onSaveInstanceState(outState)
			return
		}
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
	private fun dateKey(value: String): Long = dateKeys.getOrPut(value) { FeedXml.parseDate(value) ?: Long.MIN_VALUE }

	/** Parsed dates by their text; sorting compares each article many times. */
	private val dateKeys = HashMap<String, Long>()

	private inner class NewsMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			menu.add(0, MENU_ADD, 0, getString(R.string.news_menu_add_feed))
			menu.add(0, MENU_INCLUDED, 1, getString(R.string.news_menu_included_feeds))
			menu.add(0, MENU_REMOVE, 2, getString(R.string.news_menu_remove_feed))
			menu.add(0, MENU_TOPICS, 3, getString(R.string.mono_news_followed_topics))
			menu.add(0, MENU_REFRESH, 4, getString(R.string.news_menu_refresh))
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
			when (menuItem.itemId) {
				MENU_ADD -> askForFeed()
				MENU_INCLUDED -> showSuggestedFeeds()
				MENU_REMOVE -> showSavedFeeds()
				MENU_TOPICS -> editFollowedTopics()
				MENU_REFRESH -> load(currentFeed)
				else -> return false
			}
			return true
		}
	}

	/**
	 * First open: the included feeds are saved so News is never empty. Feeds the user removes stay removed.
	 * Each later batch has its own flag and adds only its new feeds; it also opens News on "All feeds" once.
	 */
	private fun installIncludedFeedsOnce() {
		if (!prefs.getBoolean(KEY_DEFAULTS_INSTALLED, false)) {
			val feeds = prefs.getStringSet("feeds", emptySet()).orEmpty() + INCLUDED_FEEDS_V1.map { it.second }
			prefs.edit().putStringSet("feeds", feeds).putBoolean(KEY_DEFAULTS_INSTALLED, true).apply()
		}
		if (!prefs.getBoolean(KEY_DEFAULTS_INSTALLED_V2, false)) {
			val feeds = prefs.getStringSet("feeds", emptySet()).orEmpty() + INCLUDED_FEEDS_V2.map { it.second }
			prefs.edit().putStringSet("feeds", feeds).putString("selected", ALL_FEEDS)
				.putBoolean(KEY_DEFAULTS_INSTALLED_V2, true).apply()
		}
	}

	private fun subscribedFeeds(): Set<String> = prefs.getStringSet("feeds", emptySet()).orEmpty().toSet()

	private fun feedLabel(url: String): String = INCLUDED_FEEDS.firstOrNull { it.second == url }?.first
		?: runCatching { URI(url).host?.removePrefix("www.") }.getOrNull() ?: url

	private fun renderFeedChips() {
		feedChips.removeAllViews()
		feedChips.addView(Chip(requireContext()).apply {
			text = getString(R.string.mono_news_all_feeds)
			isCheckable = true
			isChecked = currentFeed == ALL_FEEDS
			setOnClickListener {
				currentFeed = ALL_FEEDS
				prefs.edit().putString("selected", ALL_FEEDS).apply()
				renderFeedChips()
				load(ALL_FEEDS)
			}
		})
		for (url in prefs.getStringSet("feeds", emptySet()).orEmpty().sortedBy { feedLabel(it).lowercase() }) {
			feedChips.addView(Chip(requireContext()).apply {
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
		val input = EditText(requireContext()).apply {
			hint = getString(R.string.mono_news_https_rss_atom_feed_url)
			setSingleLine(true)
		}
		AlertDialog.Builder(requireContext()).setTitle(getString(R.string.mono_news_add_a_news_feed)).setView(input)
			.setPositiveButton(getString(R.string.mono_news_add)) { _, _ -> saveAndLoad(input.text.toString().trim()) }
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun editFollowedTopics() {
		val input = EditText(requireContext()).apply {
			hint = getString(R.string.mono_news_tags_to_follow_separated_by_commas)
			setText(followedTags().sorted().joinToString(", "))
		}
		AlertDialog.Builder(requireContext()).setTitle(getString(R.string.mono_news_followed_topics)).setView(input)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				val tags = input.text.toString().split(',')
					.map { it.trim() }.filter { it.length in 2..50 }.take(40).toSet()
				prefs.edit().putStringSet("tags", tags).apply()
				message.text = getString(R.string.news_topics_saved, tags.size)
				render()
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun showSuggestedFeeds() {
		AlertDialog.Builder(requireContext()).setTitle(getString(R.string.mono_news_built_in_entertainment_feeds))
			.setItems(INCLUDED_FEEDS.map { it.first }.toTypedArray()) { _, index ->
				saveAndLoad(INCLUDED_FEEDS[index].second)
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun saveAndLoad(url: String) {
		if (!NewsFeedPolicy.trustedFeed(url)) {
			message.text = getString(R.string.mono_news_for_safety_use_an_approved_https)
			return
		}
		val feeds = prefs.getStringSet("feeds", emptySet()).orEmpty().toMutableSet()
		if (feeds.size >= 25 && url !in feeds) {
			message.text = getString(R.string.mono_news_remove_a_saved_feed_before_adding)
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
		if (feeds.isEmpty()) { message.text = getString(R.string.mono_news_no_saved_feeds); return }
		AlertDialog.Builder(requireContext()).setTitle(getString(R.string.mono_news_saved_feeds))
			.setItems(feeds.map(::feedLabel).toTypedArray()) { _, which ->
				val selected = feeds[which]
				AlertDialog.Builder(requireContext()).setTitle(selected)
					.setPositiveButton(getString(R.string.mono_news_open)) { _, _ ->
						currentFeed = selected
						renderFeedChips()
						load(selected)
					}
					.setNegativeButton(getString(R.string.mono_news_remove)) { _, _ ->
						prefs.edit().putStringSet("feeds", feeds.toMutableSet().apply { remove(selected) })
							.remove(NewsFeedParser.cacheKey(selected)).apply()
						message.text = getString(R.string.mono_news_feed_removed)
						if (currentFeed == selected) {
							currentFeed = ALL_FEEDS
							prefs.edit().putString("selected", ALL_FEEDS).apply()
							load(ALL_FEEDS)
						}
						renderFeedChips()
					}
					.setNeutralButton(android.R.string.cancel, null).show()
			}.show()
	}

	/** Saved articles first, then a background refresh that replaces them. */
	private fun load(url: String, fallbackToAll: Boolean = false) {
		if (url == ALL_FEEDS) {
			loadAll()
			return
		}
		if (!NewsFeedPolicy.trustedFeed(url)) {
			if (fallbackToAll) {
				currentFeed = ALL_FEEDS
				renderFeedChips()
				loadAll()
			}
			return
		}
		feedJob?.cancel()
		val cached = cachedEntries(url)
		items = cached
		errorText = null
		noticeText = null
		loading.isVisible = true
		render()
		// Every outcome hides the progress bar itself; a cancelled job leaves it to the load that replaced it.
		feedJob = viewLifecycleOwner.lifecycleScope.launch {
			val result = fetchResult(url)
			if (url != currentFeed) return@launch
			loading.isVisible = false
			result.onSuccess { loaded ->
				items = loaded
				saveCache(url, loaded)
				render()
				restoreScroll()
			}.onFailure { error ->
				val reason = error.message.orEmpty().take(140)
				when {
					cached.isNotEmpty() -> {
						noticeText = getString(R.string.mono_news_showing_saved_articles, reason)
						render()
					}
					// The first open should show stories, not an error: try every other feed instead.
					fallbackToAll -> {
						currentFeed = ALL_FEEDS
						renderFeedChips()
						loadAll()
					}
					else -> {
						errorText = getString(R.string.mono_news_feed_unavailable, reason)
						render()
					}
				}
			}
		}
	}

	/** "All feeds": the newest articles of every subscribed feed, merged. Failing feeds are skipped. */
	private fun loadAll() {
		feedJob?.cancel()
		val feeds = subscribedFeeds().filter(NewsFeedPolicy::trustedFeed).sorted()
		val latest = feeds.associateWith(::cachedEntries).toMutableMap()
		items = merge(latest.values)
		errorText = null
		noticeText = null
		if (feeds.isEmpty()) {
			loading.isVisible = false
			render()
			return
		}
		loading.isVisible = true
		render()
		feedJob = viewLifecycleOwner.lifecycleScope.launch {
			val permits = Semaphore(MAX_PARALLEL_FEEDS)
			var showedEarly = items.isNotEmpty()
			val outcomes = feeds.map { url ->
				async {
					val result = permits.withPermit { fetchResult(url) }
					result.onSuccess { loaded ->
						if (loaded.isNotEmpty()) {
							latest[url] = loaded
							saveCache(url, loaded)
						}
						// Show the first feed that answers right away; later ones arrive in one update.
						if (!showedEarly && loaded.isNotEmpty() && currentFeed == ALL_FEEDS) {
							showedEarly = true
							items = merge(latest.values)
							render()
						}
					}
					result.isSuccess
				}
			}.awaitAll()
			if (currentFeed != ALL_FEEDS) return@launch
			loading.isVisible = false
			items = merge(latest.values)
			if (outcomes.none { it } && items.isEmpty()) {
				errorText = getString(R.string.mono_news_all_feeds_failed)
			}
			render()
			restoreScroll()
		}
	}

	private fun merge(feeds: Collection<List<NewsFeedParser.Entry>>): List<NewsFeedParser.Entry> =
		feeds.flatten().distinctBy { it.link }
			.sortedByDescending { dateKey(it.date) }
			.take(MAX_MERGED_ENTRIES)

	private fun restoreScroll() {
		if (restoredScrollY > 0 && items.isNotEmpty()) {
			val position = restoredScrollY
			restoredScrollY = 0
			listScroll.post { listScroll.scrollTo(0, position) }
		}
	}

	private fun cachedEntries(url: String): List<NewsFeedParser.Entry> =
		runCatching { NewsFeedParser.decodeCache(url, prefs.getString(NewsFeedParser.cacheKey(url), null)) }
			.getOrDefault(emptyList())

	private fun saveCache(url: String, loaded: List<NewsFeedParser.Entry>) {
		if (loaded.isEmpty()) return
		val json = runCatching { NewsFeedParser.encodeCache(url, loaded, System.currentTimeMillis()) }.getOrNull() ?: return
		prefs.edit().putString(NewsFeedParser.cacheKey(url), json).apply()
	}

	/** Network and parse errors become a failed result; cancellation still cancels. */
	private suspend fun fetchResult(url: String): Result<List<NewsFeedParser.Entry>> = try {
		Result.success(fetchEntries(url))
	} catch (cancel: CancellationException) {
		throw cancel
	} catch (error: Exception) {
		Result.failure(error)
	}

	private suspend fun fetchEntries(url: String): List<NewsFeedParser.Entry> {
		require(NewsFeedPolicy.trustedFeed(url))
		val xml = repository.fetchFeedText(url, NewsFeedParser.MAX_XML_CHARS, NewsFeedPolicy::trustedFeed)
		return withContext(Dispatchers.Default) { NewsFeedParser.parse(xml, url) }
	}

	/** One chip per followed topic; tap to narrow the list to it, tap again to clear. */
	private fun renderChips(tags: Set<String>) {
		chipsRow.removeAllViews()
		for (tag in tags.sorted()) {
			chipsRow.addView(com.google.android.material.chip.Chip(requireContext()).apply {
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
		sourceChip?.text = getString(R.string.mono_news_site) + (sourceFilter ?: "all")
		sourceChip?.isChecked = sourceFilter != null
		renderChips(tags)
		val counts = "${visible.size} articles · ${tags.size} followed topics · ${saved.size} saved" +
			if (onlyFollowing) " · followed only" else ""
		val error = errorText
		val notice = noticeText
		message.text = when {
			items.isEmpty() && error != null -> error
			items.isEmpty() && loading.isVisible -> getString(R.string.mono_news_loading_feed)
			currentFeed == ALL_FEEDS && subscribedFeeds().isEmpty() -> getString(R.string.mono_news_no_feeds_yet)
			items.isNotEmpty() && visible.isEmpty() -> getString(R.string.mono_news_no_matching_articles)
			notice != null -> "$notice\n$counts"
			else -> counts
		}
		emptyState.isVisible = visible.isEmpty()
		retryButton.isVisible = items.isEmpty() && error != null
		if (emptyState.isVisible) {
			Mascot.bind(mascot, if (error != null || items.isNotEmpty()) MascotForm.SAD else MascotForm.NEWS)
		}
		val showSite = currentFeed == ALL_FEEDS
		val pad = (12 * resources.displayMetrics.density).toInt()
		visible.forEach { entry ->
			val savedNow = entry.link in saved
			val card = MaterialCardView(requireContext()).apply {
				radius = 16 * resources.displayMetrics.density
				cardElevation = 0f
				setContentPadding(pad, pad, pad, pad)
			}
			val label = TextView(requireContext()).apply {
				val site = if (showSite) runCatching { URI(entry.link).host?.removePrefix("www.") }.getOrNull() else null
				text = (if (savedNow) "★  " else "") + entry.title +
					listOfNotNull(site, entry.date.takeIf { it.isNotBlank() }).joinToString(" · ")
						.let { if (it.isEmpty()) "" else "\n$it" } +
					(if (entry.tags.isEmpty()) "" else "\n${entry.tags.joinToString(" · ")}")
				setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
				setOnClickListener {
					if (!ExternalLinks.open(requireContext(), entry.link)) message.text = getString(R.string.mono_news_this_link_can_t_be_opened)
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
		const val KEY_DEFAULTS_INSTALLED_V2 = "included_feeds_v2"
		/** Selection value for the merged "All feeds" chip; never a URL, so it never reaches the network. */
		const val ALL_FEEDS = "all_feeds"
		const val MAX_PARALLEL_FEEDS = 4
		const val MAX_MERGED_ENTRIES = 200
		const val MENU_ADD = 8201
		const val MENU_INCLUDED = 8202
		const val MENU_REMOVE = 8203
		const val MENU_TOPICS = 8204
		const val MENU_REFRESH = 8205

		/** Installed by [KEY_DEFAULTS_INSTALLED]. */
		val INCLUDED_FEEDS_V1 = listOf(
			"Anime News Network" to "https://www.animenewsnetwork.com/news/rss.xml",
			"Anime News Network · everything" to "https://www.animenewsnetwork.com/all/rss.xml?ann-edition=us",
			"Anime Expo updates" to "https://www.anime-expo.org/blog/feed/",
			"Standard Ebooks · new releases" to "https://standardebooks.org/feeds/atom/new-releases",
		)

		/** Installed once by [KEY_DEFAULTS_INSTALLED_V2]; long-running public HTTPS feeds. */
		val INCLUDED_FEEDS_V2 = listOf(
			"Anime Corner" to "https://animecorner.me/feed/",
			"Otaku USA Magazine" to "https://otakuusamagazine.com/feed/",
			"Anime Herald" to "https://www.animeherald.com/feed/",
			"MyAnimeList news" to "https://myanimelist.net/rss/news.xml",
			"Siliconera" to "https://www.siliconera.com/feed/",
			"Book Riot" to "https://bookriot.com/feed/",
			"Reactor · science fiction and fantasy" to "https://reactormag.com/feed/",
			"NPR Books" to "https://feeds.npr.org/1032/rss.xml",
			"The Verge" to "https://www.theverge.com/rss/index.xml",
		)

		val INCLUDED_FEEDS = INCLUDED_FEEDS_V1 + INCLUDED_FEEDS_V2
	}
}
