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
import android.widget.LinearLayout
import androidx.core.graphics.Insets
import androidx.core.view.updatePadding
import androidx.core.view.isVisible
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
import org.koitharu.kotatsu.search.ui.suggestion.MainSearchScope
import org.koitharu.kotatsu.search.ui.suggestion.SearchScopeRules
import org.koitharu.kotatsu.search.ui.multi.adapter.SearchAdapter
import javax.inject.Inject

@AndroidEntryPoint
class SearchActivity :
	BaseActivity<ActivitySearchBinding>(),
	MangaListListener,
	ListSelectionController.Callback {

	/** One tap narrows the search to Manga, Books, Video or Audio sources. The switch happens in place. */
	private fun bindMediaTypeChips() {
		val group = viewBinding.chipsMediaType
		for (scope in SearchScopeRules.scopes) {
			group.addView(Chip(this).apply {
				id = View.generateViewId()
				tag = scope
				text = MainSearchScope.label(this@SearchActivity, scope)
				isCheckable = true
				isChecked = scope == viewModel.selectedMediaScope
				if (scope != "ALL") MediaAccents.tintChip(this@SearchActivity, scope, this)
			})
		}
		group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
			val selection = checkedIds.firstOrNull()?.let { chipGroup.findViewById<Chip>(it)?.tag as? String }
			if (selection != null) viewModel.setMediaScope(selection)
		}
		// Keeps the chips, the title and the menu in step with the view model (also after rotation).
		viewModel.mediaScope.observe(this) { scope ->
			for (i in 0 until group.childCount) {
				val chip = group.getChildAt(i) as? Chip ?: continue
				if (chip.tag == scope && !chip.isChecked) chip.isChecked = true
			}
			updateTitle()
			invalidateOptionsMenu()
		}
	}

	private fun updateTitle() {
		title = if (viewModel.mediaTypeOverride == MediaType.BOOK) {
			getString(R.string.monogatari_catalog_online_search_title, viewModel.query)
		} else {
			when (viewModel.kind) {
				SearchKind.SIMPLE,
				SearchKind.TITLE -> viewModel.query

				SearchKind.AUTHOR -> getString(
					R.string.inline_preference_pattern,
					getString(R.string.author),
					viewModel.query,
				)

				SearchKind.TAG -> getString(R.string.inline_preference_pattern, getString(R.string.genre), viewModel.query)
			}
		}
	}

	companion object {
		/** Intent scope for a one-off book search; never mutates the global media filter. */
		const val EXTRA_MEDIA_TYPE = "monogatari.search.media_type"
		private const val STATE_PREVIEW_MANGA_ID = "search.preview.manga_id"
		private const val STATE_PREVIEW_BOOK_URL = "search.preview.book_url"
		private const val STATE_PREVIEW_COLLAPSED = "search.preview.collapsed"
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
	private lateinit var paneController: SearchPaneController
	private lateinit var panePrefs: SearchPanePrefs
	private var previewCollapsed = false
	private var barsInsets: Insets = Insets.NONE
	private var imeBottom = 0
	private var pendingPreviewMangaId: Long? = null
	private var pendingPreviewBookUrl: String? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivitySearchBinding.inflate(layoutInflater))
		// View recreation builds a new pane, but its previously selected title should remain selected.
		pendingPreviewMangaId = savedInstanceState?.getLong(STATE_PREVIEW_MANGA_ID)?.takeIf { it != 0L }
		pendingPreviewBookUrl = savedInstanceState?.getString(STATE_PREVIEW_BOOK_URL)
		previewCollapsed = savedInstanceState?.getBoolean(STATE_PREVIEW_COLLAPSED) ?: false
		panePrefs = SearchPanePrefs(this)
		paneController = SearchPaneController(
			panes = viewBinding.root.findViewById<LinearLayout>(R.id.search_panes),
			list = viewBinding.recyclerView,
			divider = viewBinding.root.findViewById(R.id.search_pane_divider),
			preview = viewBinding.root.findViewById(R.id.search_preview_scroll),
			prefs = panePrefs,
		)
		tabletPreview = SearchTabletPreview.attach(viewBinding.root, previewCallbacks)
		applyPaneArrangement()
		viewModel.previewState.observe(this) { state ->
			tabletPreview?.render(state)
			applyPaneArrangement()
		}
		updateTitle()
		bindMediaTypeChips()
		bindFormatChips()

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
				if (isPreviewAvailable()) {
					previewCollapsed = false
					viewModel.clearPreview()
					tabletPreview?.show(book)
					applyPaneArrangement()
				} else openCatalogResult(book)
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
			// After rotation the view model still holds the selection; only process death needs a lookup.
			if (viewModel.previewState.value?.manga?.id == mangaId) {
				pendingPreviewMangaId = null
				return
			}
			val row = items.filterIsInstance<SearchResultsListModel>()
				.asSequence().flatMap { it.list.asSequence() }
				.firstOrNull { it.manga.id == mangaId }
			if (row != null) {
				viewModel.selectPreview(row.toMangaWithOverride())
				pendingPreviewMangaId = null
				return
			}
		}
		val bookUrl = pendingPreviewBookUrl
		if (bookUrl != null) {
			val row = items.filterIsInstance<BookCatalogListModel>().firstOrNull { it.url == bookUrl }
			if (row != null) {
				pane.show(row)
				applyPaneArrangement()
				pendingPreviewBookUrl = null
			}
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		tabletPreview?.selectedId?.let { outState.putLong(STATE_PREVIEW_MANGA_ID, it) }
		val mangaId = tabletPreview?.selectedId
		if (mangaId == null) pendingPreviewMangaId?.let { outState.putLong(STATE_PREVIEW_MANGA_ID, it) }
		outState.putString(STATE_PREVIEW_BOOK_URL, tabletPreview?.selectedBookUrl ?: pendingPreviewBookUrl)
		outState.putBoolean(STATE_PREVIEW_COLLAPSED, previewCollapsed)
		super.onSaveInstanceState(outState)
	}

	override fun onResume() {
		super.onResume()
		// The favourites dialog may have changed the library state of the selected title.
		viewModel.refreshPreviewLibraryState()
	}

	// ---- Adaptive panes -------------------------------------------------------------------------------------

	/** The live window (not the device orientation) decides whether a preview fits and where it goes. */
	private fun paneArrangement(collapsed: Boolean): SearchPaneArrangement {
		val config = resources.configuration
		return SearchPaneLayoutPolicy.arrangement(config.screenWidthDp, config.screenHeightDp, panePrefs.mode, collapsed)
	}

	/** True when this window has room for a preview and the user has not turned it off. */
	private fun isPreviewAvailable() = paneArrangement(collapsed = false) != SearchPaneArrangement.LIST_ONLY

	private fun applyPaneArrangement() {
		if (!::paneController.isInitialized) return
		paneController.apply(paneArrangement(previewCollapsed), tabletPreview?.hasSelection == true)
		applyPaneInsets()
		invalidateOptionsMenu()
	}

	private fun applyPaneInsets() {
		val preview = viewBinding.root.findViewById<View>(R.id.search_preview_scroll)
		val bottom = maxOf(barsInsets.bottom, imeBottom)
		val list = viewBinding.recyclerView
		when (paneController.arrangement) {
			SearchPaneArrangement.LIST_ONLY -> list.setPadding(barsInsets.left, 0, barsInsets.right, bottom)
			SearchPaneArrangement.PREVIEW_BELOW -> {
				list.setPadding(barsInsets.left, 0, barsInsets.right, 0)
				preview.updatePadding(left = barsInsets.left, right = barsInsets.right, bottom = bottom)
			}
			SearchPaneArrangement.PREVIEW_SIDE -> {
				list.setPadding(barsInsets.left, 0, 0, bottom)
				preview.updatePadding(left = 0, right = barsInsets.right, bottom = bottom)
			}
		}
	}

	/** Menu support: the toggle only makes sense while this window could show a preview. */
	internal fun canTogglePreview() = panePrefs.mode != SearchPaneMode.LIST_ONLY &&
		SearchPaneLayoutPolicy.arrangement(
			resources.configuration.screenWidthDp, resources.configuration.screenHeightDp, SearchPaneMode.AUTO, false,
		) != SearchPaneArrangement.LIST_ONLY

	internal fun isPreviewCollapsed() = previewCollapsed || paneArrangement(false) == SearchPaneArrangement.LIST_ONLY

	internal fun togglePreview() {
		previewCollapsed = !previewCollapsed
		applyPaneArrangement()
	}

	internal fun showPaneLayoutDialog() {
		val modes = SearchPaneMode.entries
		val labels = arrayOf(
			getString(R.string.search_pane_layout_auto),
			getString(R.string.search_pane_layout_list_only),
			getString(R.string.search_pane_layout_split),
		)
		MaterialAlertDialogBuilder(this)
			.setTitle(R.string.search_pane_layout)
			.setSingleChoiceItems(labels, modes.indexOf(panePrefs.mode)) { dialog, index ->
				dialog.dismiss()
				panePrefs.mode = modes[index]
				previewCollapsed = false
				applyPaneArrangement()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	/** Switching the search kind keeps the screen, query, filters, type and the selected preview. */
	internal fun onSearchKindChanged() {
		updateTitle()
		invalidateOptionsMenu()
	}

	private val previewCallbacks = object : SearchPreviewCallbacks {
		override fun onPrimary(manga: Manga) = openMangaWithSourceChoice(manga, true)
		override fun onDetails(manga: Manga) = openMangaWithSourceChoice(manga, false)
		override fun onLibrary(manga: Manga) = router.showFavoriteDialog(manga)
		override fun onDownload(manga: Manga) = router.showDownloadDialog(manga, viewBinding.root)
		override fun onChooseSource(manga: Manga) = showSourceChooser(manga) { selection ->
			viewModel.rememberPreferredSource(selection)
			viewModel.selectPreview(selection)
		}
		override fun onBrowser(manga: Manga) = router.openBrowser(manga)
		override fun onShare(manga: Manga) = ShareHelper(this@SearchActivity).shareMangaLinks(setOf(manga))
		override fun onRetry() = viewModel.retryPreview()
		override fun onBookOpen(book: BookCatalogListModel) = openCatalogResult(book)
		override fun onCollapse() {
			previewCollapsed = true
			applyPaneArrangement()
		}
		override fun hasAlternatives(manga: Manga) = viewModel.alternativeSources(manga).size >= 2
	}

	/** One chip per illustrated format; shown only while the Manga type is selected. */
	private fun bindFormatChips() {
		val group = viewBinding.chipsFormat
		for (facet in FormatFacet.entries) {
			group.addView(Chip(this).apply {
				id = View.generateViewId()
				tag = facet
				text = getString(
					when (facet) {
						FormatFacet.ALL -> R.string.search_format_all
						FormatFacet.MANGA -> R.string.search_format_manga
						FormatFacet.WEBTOON -> R.string.search_format_webtoon
						FormatFacet.COMICS -> R.string.search_format_comics
						FormatFacet.MANHWA -> R.string.search_format_manhwa
						FormatFacet.MANHUA -> R.string.search_format_manhua
					},
				)
				isCheckable = true
				isChecked = facet == viewModel.formatFacet.value
			})
		}
		group.setOnCheckedStateChangeListener { chipGroup, checkedIds ->
			val facet = checkedIds.firstOrNull()?.let { chipGroup.findViewById<Chip>(it)?.tag as? FormatFacet }
			if (facet != null) viewModel.setFormatFacet(facet)
		}
		viewBinding.chipFormatUnclassified.setOnCheckedChangeListener { _, checked ->
			viewModel.setIncludeUnclassifiedFormats(checked)
		}
		viewModel.formatFacet.observe(this) { facet ->
			for (i in 0 until group.childCount) {
				val chip = group.getChildAt(i) as? Chip ?: continue
				if (chip.tag == facet && !chip.isChecked) chip.isChecked = true
			}
			// Restrictive facets disclose that titles without format information are left out.
			viewBinding.chipFormatUnclassified.isVisible = facet != FormatFacet.ALL
		}
		viewModel.includesUnclassifiedFormats.observe(this) {
			if (viewBinding.chipFormatUnclassified.isChecked != it) viewBinding.chipFormatUnclassified.isChecked = it
		}
		viewModel.mediaScope.observe(this) { scope ->
			viewBinding.scrollFormat.isVisible = scope == MediaType.MANGA.name
		}
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val barsInsets = insets.systemBarsInsets
		viewBinding.toolbar.updatePadding(
			top = barsInsets.top,
			left = barsInsets.left,
			right = barsInsets.right,
		)
		this.barsInsets = barsInsets
		imeBottom = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
		if (::paneController.isInitialized) applyPaneInsets()
		return insets.consumeAllSystemBarsInsets()
	}

	override fun onItemClick(item: MangaListModel, view: View) {
		if (!selectionController.onItemClick(item.id)) {
			if (isPreviewAvailable()) {
				previewCollapsed = false
				viewModel.selectPreview(item.toMangaWithOverride())
				applyPaneArrangement()
			} else openMangaWithSourceChoice(item.toMangaWithOverride(), false)
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
		showSourceChooser(manga) { selection ->
			viewModel.rememberPreferredSource(selection)
			if (read) router.openReader(selection) else router.openDetails(selection)
		}
	}

	private fun showSourceChooser(manga: Manga, onChosen: (Manga) -> Unit) {
		val alternatives = viewModel.alternativeSources(manga)
		if (alternatives.size < 2) return
		val preferred = viewModel.preferredSourceName(manga)
		val ordered = alternatives.sortedWith(compareByDescending<Manga> { it.source.name == preferred }
			.thenBy { it.source.name })
		MaterialAlertDialogBuilder(this)
			.setTitle(getString(R.string.search_choose_source, manga.title))
			.setItems(ordered.map {
				(if (it.source.name == preferred) "★ " else "") +
					it.source.getTitle(this).toString()
			}.toTypedArray()) { _, index -> onChosen(ordered[index]) }
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
