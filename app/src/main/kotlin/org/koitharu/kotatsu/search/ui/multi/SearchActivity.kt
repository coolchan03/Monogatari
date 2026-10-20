package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.media.MediaAccents
import com.google.android.material.chip.Chip
import org.koitharu.kotatsu.core.util.ExternalLinks
import android.widget.Toast
import android.content.Intent
import android.os.Bundle
import java.io.File
import okhttp3.OkHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.core.model.getTitle
import androidx.appcompat.view.ActionMode
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.PublicBookCatalog
import org.koitharu.kotatsu.core.media.OfficialEpubDownloader
import org.koitharu.kotatsu.core.media.BookShelf
import org.koitharu.kotatsu.core.media.StandardEbooksCatalog
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.media.StoryWeaverCatalog
import org.koitharu.kotatsu.core.media.StoryWeaverReaderActivity
import org.koitharu.kotatsu.core.media.OfficialMediaSites
import org.koitharu.kotatsu.core.media.OfficialFictionBrowserActivity
import org.koitharu.kotatsu.core.exceptions.resolve.ExceptionResolver
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.list.ListSelectionController
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.ui.widgets.TipView
import org.koitharu.kotatsu.core.util.ShareHelper
import org.koitharu.kotatsu.core.util.ext.consumeAllSystemBarsInsets
import org.koitharu.kotatsu.core.util.ext.invalidateNestedItemDecorations
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.systemBarsInsets
import org.koitharu.kotatsu.databinding.ActivitySearchBinding
import org.koitharu.kotatsu.list.domain.ListFilterOption
import org.koitharu.kotatsu.list.ui.MangaSelectionDecoration
import org.koitharu.kotatsu.list.ui.adapter.MangaListListener
import org.koitharu.kotatsu.list.ui.adapter.TypedListSpacingDecoration
import org.koitharu.kotatsu.list.ui.model.ListHeader
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.list.ui.size.DynamicItemSizeResolver
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.multi.adapter.SearchAdapter
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity :
	BaseActivity<ActivitySearchBinding>(),
	MangaListListener,
	ListSelectionController.Callback {

	/** One tap narrows the search to Manga, Books, Video or Audio sources; the sources are filtered before querying. */
	private fun bindMediaTypeChips() {
		val group = viewBinding.chipsMediaType
		val scopes = listOf("ALL") + MediaType.entries.map { it.name }
		for (scope in scopes) {
			group.addView(Chip(this).apply {
				id = View.generateViewId()
				tag = scope
				text = when (scope) {
					MediaType.MANGA.name -> getString(R.string.search_media_manga)
					MediaType.BOOK.name -> getString(R.string.search_media_books)
					MediaType.VIDEO.name -> getString(R.string.search_media_video)
					MediaType.AUDIO.name -> getString(R.string.search_media_audio)
					else -> getString(R.string.search_media_all)
				}
				isCheckable = true
				isChecked = scope == viewModel.selectedMediaScope
				if (scope != "ALL") MediaAccents.tintChip(this@SearchActivity, scope, this)
			})
		}
		group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
			val selection = checkedIds.firstOrNull()?.let { chipGroup.findViewById<Chip>(it)?.tag as? String }
			if (selection == null || selection == viewModel.selectedMediaScope) return@setOnCheckedStateChangeListener
			startActivity(Intent(this, SearchActivity::class.java)
				.putExtra(AppRouter.KEY_QUERY, viewModel.query)
				.putExtra(AppRouter.KEY_KIND, viewModel.kind)
				.putExtra(EXTRA_MEDIA_TYPE, selection))
			finish()
			@Suppress("DEPRECATION")
			overridePendingTransition(0, 0)
		}
	}

	companion object {
		/** Intent scope for a one-off book search; never mutates the global media filter. */
		const val EXTRA_MEDIA_TYPE = "monogatari.search.media_type"
		private const val STATE_PREVIEW_MANGA_ID = "search.preview.manga_id"
		private const val STATE_PREVIEW_BOOK_URL = "search.preview.book_url"
	}

	@Inject
	lateinit var settings: AppSettings
	@Inject
	lateinit var publicBookCatalog: PublicBookCatalog
	@Inject @BaseHttpClient
	lateinit var epubClient: OkHttpClient

	private val viewModel by viewModels<SearchViewModel>()
	private lateinit var selectionController: ListSelectionController
	private var tabletPreview: SearchTabletPreview? = null
	private var pendingPreviewMangaId: Long? = null
	private var pendingPreviewBookUrl: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySearchBinding.inflate(layoutInflater))
		// View recreation builds a new pane, but its previously selected title should remain selected.
		pendingPreviewMangaId = savedInstanceState?.getLong(STATE_PREVIEW_MANGA_ID)?.takeIf { it != 0L }
		pendingPreviewBookUrl = savedInstanceState?.getString(STATE_PREVIEW_BOOK_URL)
		tabletPreview = SearchTabletPreview.attach(
			root = viewBinding.root,
			onDetails = { openMangaWithSourceChoice(it, false) },
			onRead = { openMangaWithSourceChoice(it, true) },
			onBookOpen = { openCatalogResult(it) },
		)
		title = when (viewModel.kind) {
			SearchKind.SIMPLE,
			SearchKind.TITLE -> viewModel.query

			SearchKind.AUTHOR -> getString(
				R.string.inline_preference_pattern,
				getString(R.string.author),
				viewModel.query,
			)

			SearchKind.TAG -> getString(R.string.inline_preference_pattern, getString(R.string.genre), viewModel.query)
		}

		if (viewModel.mediaTypeOverride == MediaType.BOOK) {
			title = getString(R.string.monogatari_catalog_online_search_title, viewModel.query)
		}
		bindMediaTypeChips()

		val itemClickListener = OnListItemClickListener<SearchResultsListModel> { item, view ->
			if (view.id == R.id.button_retry) {
				retrySection(item)
			} else if (item.listFilter == null) {
				router.openSearch(item.source, viewModel.query)
			} else {
				router.openList(item.source, item.listFilter, item.sortOrder)
			}
		}
		val sizeResolver = DynamicItemSizeResolver(resources, this, settings, adjustWidth = true)
		val selectionDecoration = MangaSelectionDecoration(this)
		selectionController = ListSelectionController(
			appCompatDelegate = delegate,
			decoration = selectionDecoration,
			registryOwner = this,
			callback = this,
		)
		val adapter = SearchAdapter(
			listener = this,
			itemClickListener = itemClickListener,
			sizeResolver = sizeResolver,
			selectionDecoration = selectionDecoration,
			onBookOpen = { book ->
				if (tabletPreview != null) tabletPreview?.show(book)
				else openCatalogResult(book)
			},
		)
		viewBinding.recyclerView.adapter = adapter
		viewBinding.recyclerView.setHasFixedSize(true)
		viewBinding.recyclerView.addItemDecoration(TypedListSpacingDecoration(this, true))

		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		supportActionBar?.setSubtitle(R.string.search_results)

		addMenuProvider(SearchMenuProvider(this, viewModel))

		viewModel.list.observe(this, adapter)
		viewModel.list.observe(this) { items -> restoreTabletPreview(items) }
		viewModel.onError.observeEvent(this, SnackbarErrorObserver(viewBinding.recyclerView, null))
	}

	private fun restoreTabletPreview(items: List<org.koitharu.kotatsu.list.ui.model.ListModel>) {
		val pane = tabletPreview ?: return
		val mangaId = pendingPreviewMangaId
		if (mangaId != null) {
			val row = items.filterIsInstance<SearchResultsListModel>()
				.asSequence().flatMap { it.list.asSequence() }
				.firstOrNull { it.manga.id == mangaId }
			if (row != null) {
				pane.show(row)
				pendingPreviewMangaId = null
				return
			}
		}
		val bookUrl = pendingPreviewBookUrl
		if (bookUrl != null) {
			val row = items.filterIsInstance<BookCatalogListModel>().firstOrNull { it.url == bookUrl }
			if (row != null) {
				pane.show(row)
				pendingPreviewBookUrl = null
			}
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		tabletPreview?.selectedId?.let { outState.putLong(STATE_PREVIEW_MANGA_ID, it) }
		val mangaId = tabletPreview?.selectedId
		if (mangaId == null) pendingPreviewMangaId?.let { outState.putLong(STATE_PREVIEW_MANGA_ID, it) }
		outState.putString(STATE_PREVIEW_BOOK_URL, tabletPreview?.selectedBookUrl ?: pendingPreviewBookUrl)
		super.onSaveInstanceState(outState)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.toolbar.updatePadding(
			top = barsInsets.top,
			left = barsInsets.left,
			right = barsInsets.right,
		)
		val preview = viewBinding.root.findViewById<View>(R.id.search_preview_scroll)
		preview?.updatePadding(right = barsInsets.right, bottom = barsInsets.bottom)
		viewBinding.recyclerView.setPadding(
			left = barsInsets.left,
			top = 0,
			right = if (preview != null) 0 else barsInsets.right,
			bottom = barsInsets.bottom,
		)
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onItemClick(item: MangaListModel, view: View) {
		if (!selectionController.onItemClick(item.id)) {
			if (tabletPreview != null) tabletPreview?.show(item) else openMangaWithSourceChoice(item.toMangaWithOverride(), false)
		}
	}

	override fun onItemLongClick(item: MangaListModel, view: View): Boolean {
		return selectionController.onItemLongClick(view, item.id)
	}

	override fun onItemContextClick(item: MangaListModel, view: View): Boolean {
		return selectionController.onItemContextClick(view, item.id)
	}

	override fun onReadClick(manga: Manga, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			openMangaWithSourceChoice(manga, true)
		}
	}

	override fun onTagClick(manga: Manga, tag: MangaTag, view: View) {
		if (!selectionController.onItemClick(manga.id)) {
			router.openList(tag)
		}
	}

	/** A global book result has the same offline actions as Books, on phones and tablet panes. */
	private fun openCatalogResult(book: BookCatalogListModel) {
		if (book.isOfficialSiteSearch || book.catalog == "StoryWeaver") {
			openCatalogWebsite(book.url, book.attribution)
			return
		}
		val direct = book.downloadUrl?.takeIf { OfficialEpubDownloader.approved(book.catalog, it) }
		val canResolve = book.catalog == "Standard Ebooks" && StandardEbooksCatalog.officialPage(book.url)
		if (direct == null && !canResolve) {
			openCatalogWebsite(book.url, book.attribution)
			return
		}
		val labels = listOf(
			getString(R.string.search_book_save_offline),
			getString(R.string.search_book_official_listing),
		) + if (direct != null) listOf(getString(R.string.search_book_open_in_browser)) else emptyList()
		MaterialAlertDialogBuilder(this).setTitle(book.title)
			.setItems(labels.toTypedArray()) { _, index ->
				when (index) {
					0 -> saveCatalogBook(book)
					1 -> openCatalogWebsite(book.url, book.attribution)
					2 -> direct?.let { openCatalogWebsite(it) }
				}
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun saveCatalogBook(book: BookCatalogListModel) {
		val progress = Snackbar.make(viewBinding.root,
			getString(R.string.monogatari_epub_downloading, book.title), Snackbar.LENGTH_INDEFINITE)
		val download = lifecycleScope.launch {
			try {
				val url = if (book.catalog == "Standard Ebooks") withContext(Dispatchers.IO) {
					publicBookCatalog.resolveStandardEbooksEpub(book.url)
				} else book.downloadUrl
				if (url == null || !OfficialEpubDownloader.approved(book.catalog, url)) {
					Snackbar.make(viewBinding.root, R.string.monogatari_epub_unapproved, Snackbar.LENGTH_LONG).show()
					return@launch
				}
				val stored = OfficialEpubDownloader.save(epubClient, File(filesDir, "imported-books"),
					book.title, book.catalog, url)
				Snackbar.make(viewBinding.root, getString(R.string.monogatari_epub_saved, book.title),
					Snackbar.LENGTH_SHORT).show()
				startActivity(BookShelf.readerIntent(this@SearchActivity, stored))
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Exception) {
				Snackbar.make(viewBinding.root,
					getString(R.string.monogatari_epub_download_error, error.message.orEmpty().take(130)),
					Snackbar.LENGTH_LONG).show()
			} finally {
				progress.dismiss()
			}
		}
		progress.setAction(android.R.string.cancel) { download.cancel() }
		progress.show()
	}

	/** StoryWeaver opens its authorized illustrated reader in-app, rather than an unrelated browser tab. */
	private fun openCatalogWebsite(url: String, attribution: String? = null) {
		if (StoryWeaverCatalog.officialStoryUrl(url)) {
			startActivity(Intent(this, StoryWeaverReaderActivity::class.java)
				.putExtra(StoryWeaverReaderActivity.EXTRA_URL, url)
				.putExtra(StoryWeaverReaderActivity.EXTRA_CREDIT, attribution))
		} else if (OfficialMediaSites.fictionSite(url) != null) {
			startActivity(Intent(this, OfficialFictionBrowserActivity::class.java)
				.putExtra(OfficialFictionBrowserActivity.EXTRA_URL, url))
		} else if (!ExternalLinks.open(this, url)) {
			Toast.makeText(this, R.string.link_cannot_open, Toast.LENGTH_SHORT).show()
		}
	}

	/** Cross-source choices are explicit. Picking one also persists the preferred provider for this work. */
	private fun openMangaWithSourceChoice(manga: Manga, read: Boolean) {
		val alternatives = viewModel.alternativeSources(manga)
		if (alternatives.size < 2) {
			if (read) router.openReader(manga) else router.openDetails(manga)
			return
		}
		val preferred = viewModel.preferredSourceName(manga)
		val ordered = alternatives.sortedWith(compareByDescending<Manga> { it.source.name == preferred }
			.thenBy { it.source.name })
		MaterialAlertDialogBuilder(this)
			.setTitle(getString(R.string.search_choose_source, manga.title))
			.setItems(ordered.map {
				(if (it.source.name == preferred) "★ " else "") +
					it.source.getTitle(this).toString()
			}.toTypedArray()) { _, index ->
				val selection = ordered[index]
				viewModel.rememberPreferredSource(selection)
				if (read) router.openReader(selection) else router.openDetails(selection)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun retrySection(item: SearchResultsListModel) {
		val error = item.error
		if (error != null && ExceptionResolver.canResolve(error)) {
			lifecycleScope.launch {
				if (exceptionResolver.resolve(error)) viewModel.retrySource(item.source)
			}
		} else {
			viewModel.retrySource(item.source)
		}
	}

	override fun onRetryClick(error: Throwable) {
		viewModel.retry()
	}

	override fun onFilterOptionClick(option: ListFilterOption) = Unit

	override fun onFilterClick(view: View?) = Unit

	override fun onEmptyActionClick() = viewModel.continueSearch()

	override fun onListHeaderClick(item: ListHeader, view: View) = Unit

	override fun onFooterButtonClick() = viewModel.continueSearch()

	override fun onPrimaryButtonClick(tipView: TipView) = Unit

	override fun onSecondaryButtonClick(tipView: TipView) = Unit

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding.recyclerView.invalidateNestedItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_remote, menu)
		return true
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_share -> {
				ShareHelper(this).shareMangaLinks(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_favourite -> {
				router.showFavoriteDialog(collectSelectedItems())
				mode?.finish()
				true
			}

			R.id.action_save -> {
				router.showDownloadDialog(collectSelectedItems(), viewBinding.recyclerView)
				mode?.finish()
				true
			}

			else -> false
		}
	}

	private fun collectSelectedItems(): Set<Manga> {
		return viewModel.getItems(selectionController.peekCheckedIds())
	}
}
