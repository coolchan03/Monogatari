package org.koitharu.kotatsu.discover

import android.os.Bundle
import android.content.Intent
import android.widget.EditText
import android.widget.LinearLayout
import com.google.android.material.button.MaterialButton
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
import androidx.appcompat.app.AppCompatActivity
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
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import org.koitharu.kotatsu.databinding.ActivityDiscoverBinding
import org.koitharu.kotatsu.databinding.ItemDiscoverBinding
import javax.inject.Inject

/** AniList trending/popular lists, including Boys' Love; a tap searches every enabled source for the title. */
@AndroidEntryPoint
class DiscoverActivity : AppCompatActivity() {

	/** Discover finds what to enjoy; Explore remains the advanced source manager. */
	private enum class MediaSection(val label: String, val description: String) {
		MANGA("Manga & Comics", "Trending comics and Boys' Love titles from AniList"),
		BOOK("Books & Novels", "Light novels, free ebooks and public catalogs with cover art"),
		VIDEO("Anime & Video", "Anime recommendations and public video collections"),
		AUDIO("Audio", "Browse public audiobooks or search installed audio-drama sources"),
		NEWS("News", "Read RSS articles and follow your favorite topics"),
	}

	@Inject
	lateinit var discover: AniListDiscover

	@Inject
	lateinit var publicCatalog: PublicBookCatalog

	@Inject
	lateinit var coil: ImageLoader

	private lateinit var binding: ActivityDiscoverBinding
	private val adapter = ItemsAdapter()
	private var section = MediaSection.MANGA
	private var feed = AniListDiscover.Feed.TRENDING_BL
	private var publicFeedSelected = false
	private var nextPage = 1
	private var hasNextPage = true
	private var loadJob: Job? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityDiscoverBinding.inflate(layoutInflater)
		setTitle(R.string.discover)
		binding.discoverIntro.setText(R.string.discover_intro_explained)
		// Toolbar with Up, plus edge-to-edge insets (status/navigation bars, cutouts, ChromeOS caption bar).
		setMonogatariContent(binding.root)

		savedInstanceState?.getString(KEY_SECTION)?.let { name ->
			MediaSection.entries.find { it.name == name }?.let { section = it }
		}
		savedInstanceState?.getString(KEY_FEED)?.let { name ->
			AniListDiscover.Feed.entries.find { it.name == name }?.let { feed = it }
		}
		publicFeedSelected = savedInstanceState?.getBoolean(KEY_PUBLIC_FEED) ?: false

		MediaSection.entries.forEach { media ->
			binding.chipsMedia.addView(Chip(this).apply {
				id = media.ordinal + 100
				text = media.label
				isCheckable = true
				isChecked = media == section
				MediaAccents.tintChip(this@DiscoverActivity, media.name, this)
			})
		}
		binding.chipsMedia.setOnCheckedStateChangeListener { _, checkedIds ->
			val chosen = checkedIds.firstOrNull()?.let { MediaSection.entries.getOrNull(it - 100) }
			if (chosen != null && chosen != section) {
				section = chosen
				publicFeedSelected = section == MediaSection.AUDIO
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
			if (PUBLIC_CATALOG_FEED_ID in checkedIds) {
				if (!publicFeedSelected) {
					publicFeedSelected = true
					reload()
				}
			} else {
				val selected = checkedIds.firstOrNull()?.let { AniListDiscover.Feed.entries.getOrNull(it - 1) }
				if (selected != null && (selected != feed || publicFeedSelected)) {
					publicFeedSelected = false
					feed = selected
					reload()
				}
			}
		}
		showSection()

		// Reflow covers from phone portrait to 13-inch tablets and resizable windows.
		val columns = (resources.configuration.screenWidthDp / 176).coerceIn(2, 7)
		val layoutManager = GridLayoutManager(this, columns)
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
		reload()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(KEY_FEED, feed.name)
		outState.putString(KEY_SECTION, section.name)
		outState.putBoolean(KEY_PUBLIC_FEED, publicFeedSelected)
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}

	private fun showSection() {
		binding.discoverSectionDescription.text = section.description
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
		(binding.chipsFeed.parent as ViewGroup).isVisible = options.isNotEmpty() || publicLabel != null
		options.forEach { entry ->
			binding.chipsFeed.addView(Chip(this).apply {
				id = entry.ordinal + 1
				setText(entry.titleRes)
				isCheckable = true
				isChecked = entry == feed && !publicFeedSelected
				MediaAccents.tintChip(this@DiscoverActivity, section.name, this)
			})
		}
		publicLabel?.let { label ->
			binding.chipsFeed.addView(Chip(this).apply {
				id = PUBLIC_CATALOG_FEED_ID
				text = label
				isCheckable = true
				isChecked = publicFeedSelected
				MediaAccents.tintChip(this@DiscoverActivity, section.name, this)
			})
		}
		binding.discoverActions.removeAllViews()
		fun action(title: String, run: () -> Unit) {
			binding.discoverActions.addView(MaterialButton(this).apply {
				text = title
				isAllCaps = false
				MediaAccents.tintButton(this@DiscoverActivity, section.name, this)
				setOnClickListener { run() }
			}, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
				marginEnd = (8 * resources.displayMetrics.density).toInt()
			})
		}
		when (section) {
			MediaSection.MANGA -> {
				action("Search manga") { askSearch(MediaType.MANGA) }
				action("My library") { startActivity(Intent(this, UnifiedLibraryActivity::class.java)) }
			}
			MediaSection.BOOK -> {
				action("Books catalog & OPDS") { startActivity(Intent(this, BookLibraryActivity::class.java)) }
				action("Search novel sources") { askSearch(MediaType.BOOK) }
			}
			MediaSection.VIDEO -> {
				action("Search video websites") { askSearch(MediaType.VIDEO) }
				action("Archive public videos") { searchArchiveVideos() }
				action("Video RSS feeds") { openMediaFeeds(MediaType.VIDEO) }
				action("My saved videos") { openLibrary(MediaType.VIDEO) }
			}
			MediaSection.AUDIO -> {
				action("Find audiobooks & dramas") { askSearch(MediaType.AUDIO) }
				action("Fiction podcasts & audio drama") { openMediaFeeds(MediaType.AUDIO) }
				action("My audiobooks") { openLibrary(MediaType.AUDIO) }
			}
			MediaSection.NEWS -> {
				action("News & followed tags") { startActivity(Intent(this, NewsFeedActivity::class.java)) }
			}
		}
		// Discover recommends titles; Explore browses the source websites themselves.
		action(getString(R.string.discover_browse_sources)) { openExploreTab() }
	}

	private fun openExploreTab() {
		startActivity(
			Intent(this, org.koitharu.kotatsu.main.ui.MainActivity::class.java)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
				.putExtra(org.koitharu.kotatsu.main.ui.MainActivity.EXTRA_OPEN_EXPLORE, true),
		)
	}

	private fun openMediaFeeds(type: MediaType) {
		startActivity(Intent(this, MediaFeedActivity::class.java)
			.putExtra(MediaFeedActivity.EXTRA_MEDIA_TYPE, type.name))
	}

	private fun openLibrary(type: MediaType) {
		startActivity(Intent(this, UnifiedLibraryActivity::class.java)
			.putExtra(UnifiedLibraryActivity.EXTRA_MEDIA_KIND, type.name))
	}

	private fun askSearch(type: MediaType) {
		val input = EditText(this).apply {
			hint = "Title, author or topic"
			setSingleLine(true)
		}
		MaterialAlertDialogBuilder(this)
			.setTitle("Search ${type.name.lowercase().replaceFirstChar { it.uppercaseChar() }} sources")
			.setView(input)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton("Search") { _, _ ->
				val term = input.text?.toString()?.trim().orEmpty()
				if (term.isNotBlank()) openTypedSearch(term, type)
			}
			.show()
	}

	private fun searchArchiveVideos() {
		val input = EditText(this).apply { hint = "Search public video archives"; setSingleLine(true) }
		MaterialAlertDialogBuilder(this)
			.setTitle("Internet Archive videos")
			.setView(input)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton("Search") { _, _ ->
				val term = input.text?.toString()?.trim().orEmpty()
				if (term.isBlank()) return@setPositiveButton
				lifecycleScope.launch {
					val videos = runCatching { publicCatalog.searchInternetArchiveVideos(term) }
					videos.onSuccess { results ->
						if (results.isEmpty()) {
							showMessage("No public Archive videos matched that search.")
						} else {
							MaterialAlertDialogBuilder(this@DiscoverActivity)
								.setTitle("Internet Archive · ${results.size} results")
								.setItems(results.map { it.title }.toTypedArray()) { _, position ->
									startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(results[position].url)))
								}
								.setNegativeButton(android.R.string.cancel, null).show()
						}
					}.onFailure { showMessage("Archive video search unavailable: ${it.message.orEmpty().take(100)}") }
				}
			}.show()
	}

	private fun openTypedSearch(title: String, type: MediaType) {
		startActivity(Intent(this, SearchActivity::class.java)
			.putExtra(AppRouter.KEY_QUERY, title)
			.putExtra(AppRouter.KEY_KIND, SearchKind.SIMPLE)
			.putExtra(SearchActivity.EXTRA_MEDIA_TYPE, type.name))
	}

	private fun reload() {
		loadJob?.cancel()
		loadJob = null
		adapter.items = emptyList()
		nextPage = 1
		hasNextPage = section in setOf(MediaSection.MANGA, MediaSection.BOOK, MediaSection.VIDEO) ||
			(section == MediaSection.AUDIO && publicFeedSelected)
		if (hasNextPage) {
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

	private fun loadMore() {
		if (loadJob?.isActive == true || !hasNextPage) return
		val requestedFeed = feed
		val requestedSection = section
		val requestedPublic = publicFeedSelected
		val page = nextPage
		binding.progress.isVisible = true
		binding.textMessage.isVisible = false
		loadJob = lifecycleScope.launch {
			try {
				val result = if (requestedPublic) loadPublicFeed(requestedSection, page)
					else discover.load(requestedFeed, page)
				if (requestedFeed != feed || requestedSection != section ||
					requestedPublic != publicFeedSelected) return@launch
				val additions = result.items.filter { new -> adapter.items.none { it.id == new.id } }
				adapter.items = adapter.items + additions
				hasNextPage = result.hasNextPage && additions.isNotEmpty()
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
				binding.progress.isVisible = false
			}
		}
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

	private fun showMessage(text: String) {
		binding.textMessage.text = text
		binding.textMessage.isVisible = true
	}

	private class Holder(val binding: ItemDiscoverBinding) : RecyclerView.ViewHolder(binding.root)

	private inner class ItemsAdapter : RecyclerView.Adapter<Holder>() {

		var items: List<AniListDiscover.Item> = emptyList()
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
			val badge = listOfNotNull(item.providerLabel, item.score?.let { "$it%" }, if (item.isAdult) "18+" else null)
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
				MediaSection.BOOK -> MediaType.BOOK
				MediaSection.VIDEO -> MediaType.VIDEO
				else -> MediaType.MANGA
			}
			holder.binding.root.setOnClickListener {
				val link = item.officialUrl
				if (link != null && link.startsWith("https://")) {
					runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link))) }
						.onFailure { showMessage("No browser is available for this catalog link.") }
				} else openTypedSearch(item.title, type)
			}
			holder.binding.root.setOnLongClickListener {
				val alternative = item.alternativeTitle ?: return@setOnLongClickListener false
				openTypedSearch(alternative, type)
				true
			}
		}
	}

	private companion object {

		const val KEY_FEED = "feed"
		const val KEY_SECTION = "media_section"
		const val KEY_PUBLIC_FEED = "public_catalog_feed"
		const val PUBLIC_CATALOG_FEED_ID = 11000
	}
}
