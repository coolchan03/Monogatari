package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.DragEvent
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.ActivityCompat
import androidx.core.net.toUri
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.builtin.opds.OpdsSourceRegistry
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.qa.CsvExportFormatter
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.util.ExternalLinks
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import java.io.File
import javax.inject.Inject

/**
 * Initial book catalog and local ebook shelf; no DRM or lending bypass.
 * Shown as a main navigation tab, or inside [BookLibraryActivity] when opened from elsewhere.
 */
@AndroidEntryPoint
class BookCatalogFragment : Fragment() {
	@Inject lateinit var catalog: PublicBookCatalog
	@Inject lateinit var imageLoader: ImageLoader
	@Inject lateinit var opds: OpdsCatalog
	@Inject @BaseHttpClient lateinit var epubClient: OkHttpClient
	private val resultsAdapter = CatalogResultsAdapter()
	private lateinit var query: EditText
	private lateinit var catalogStatus: TextView
	private var currentBooks = emptyList<PublicBookCatalog.Book>()
	private var currentProviderErrors = emptyList<String>()
	private var catalogJob: Job? = null
	private var lastCatalogProvider: String? = null
	private var catalogOrder = BookCatalogSorter.Order.RELEVANCE
	private val catalogPrefs by lazy { requireContext().getSharedPreferences("book_catalog_ui", Context.MODE_PRIVATE) }
	private val isBottomTab: Boolean
		get() = arguments?.getBoolean(AppRouter.KEY_IS_BOTTOMTAB, false) == true
	private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
		importBooks(uris)
	}

	/** Copies picked or drag-and-dropped files into the local library. */
	private fun importBooks(uris: List<Uri>) {
		if (uris.isEmpty()) return
		val context = requireContext()
		viewLifecycleOwner.lifecycleScope.launch {
			val report = withContext(Dispatchers.IO) {
				// Shared with Add files, so both import books the same way.
				uris.map { uri -> LocalFileImporter.importBook(context, uri).message }
			}
			showLines(report)
		}
	}

	/**
	 * Drag and drop (ChromeOS, desktop windows, tablets in split screen): dropping EPUB/PDF/text files anywhere
	 * on this screen imports them, the same as the Import button.
	 */
	private fun enableFileDrop(target: View) {
		target.setOnDragListener { _, event ->
			when (event.action) {
				// Accept any file drag; unsupported formats are reported by the import itself.
				DragEvent.ACTION_DRAG_STARTED -> event.clipDescription != null

				DragEvent.ACTION_DROP -> {
					val clip = event.clipData ?: return@setOnDragListener false
					val permissions = ActivityCompat.requestDragAndDropPermissions(requireActivity(), event)
					val uris = (0 until clip.itemCount).mapNotNull { clip.getItemAt(it).uri }
					if (uris.isEmpty()) {
						permissions?.release()
						return@setOnDragListener false
					}
					importBooks(uris)
					// Keep the grant until the copy has had time to open the files.
					target.postDelayed({ permissions?.release() }, 60_000L)
					true
				}

				else -> true
			}
		}
	}

	private val exportPicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
		val ctx = context ?: return@registerForActivityResult
		if (uri != null) viewLifecycleOwner.lifecycleScope.launch {
			val result = withContext(Dispatchers.IO) {
				runCatching {
					val books = BookShelf.list(ctx)
					val csv = CsvExportFormatter.encode(listOf("Title", "Format", "Progress"), books.map {
						listOf(BookShelf.title(it), it.extension.lowercase(), BookShelf.progress(ctx, it))
					})
					ctx.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
						?: error(getString(R.string.monogatari_catalog_export_destination_error))
					getString(R.string.monogatari_catalog_exported, books.size)
				}.getOrElse { getString(R.string.monogatari_catalog_export_failed, it.message.orEmpty()) }
			}
			showLines(listOf(result))
		}
	}

	private inner class CatalogMenuProvider : MenuProvider {

		override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
			// Secondary actions live here so the screen itself shows only search and providers.
			menu.add(0, MENU_IMPORT, 0, R.string.monogatari_catalog_import)
			menu.add(0, MENU_MY_BOOKS, 1, R.string.monogatari_catalog_local)
			menu.add(0, MENU_FIND_IMPORTED, 2, R.string.monogatari_catalog_filter)
			menu.add(0, MENU_OPDS_OPEN, 3, R.string.monogatari_catalog_opds_open)
			menu.add(0, MENU_OPDS_SAVE, 4, R.string.monogatari_catalog_opds_save)
			menu.add(0, MENU_OPDS_SAVED, 5, R.string.monogatari_catalog_opds_saved)
			menu.add(0, 7203, 6, R.string.monogatari_catalog_menu_export)
		}

		override fun onMenuItemSelected(menuItem: MenuItem): Boolean = onCatalogMenuItemSelected(menuItem)
	}

	private fun onCatalogMenuItemSelected(item: MenuItem): Boolean {
		when (item.itemId) {
			7203 -> exportPicker.launch("monogatari-books.csv")
			MENU_IMPORT -> picker.launch(arrayOf("*/*"))
			MENU_MY_BOOKS -> showLocalBooks()
			MENU_FIND_IMPORTED -> showLocalBooks(query.text.toString().trim())
			MENU_OPDS_OPEN -> openOpds(query.text.toString().trim())
			MENU_OPDS_SAVE -> saveOpds(query.text.toString().trim())
			MENU_OPDS_SAVED -> showSavedOpds()
			else -> return false
		}
		return true
	}

	override fun onCreateView(
		inflater: LayoutInflater,
		container: ViewGroup?,
		savedInstanceState: Bundle?,
	): View = inflater.inflate(R.layout.activity_monogatari_book_catalog, container, false)

	private fun <T : View> findViewById(id: Int): T = requireView().findViewById(id)

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		addMenuProvider(CatalogMenuProvider())
		Mascot.bind(findViewById<ImageView>(R.id.monogatari_catalog_mascot), MascotForm.BOOK)
		enableFileDrop(view)
		query = findViewById(R.id.monogatari_catalog_query)
		query.setText(savedInstanceState?.getString("catalog_query")
			?: catalogPrefs.getString("last_query", "").orEmpty())
		catalogStatus = findViewById(R.id.monogatari_catalog_status)
		resultsAdapter.imageLoader = imageLoader
		catalogOrder = runCatching {
			BookCatalogSorter.Order.valueOf(
				catalogPrefs
					.getString("sort", BookCatalogSorter.Order.RELEVANCE.name)!!
			)
		}.getOrDefault(BookCatalogSorter.Order.RELEVANCE)
		findViewById<Chip>(R.id.monogatari_catalog_sort).apply {
			text = getString(R.string.monogatari_catalog_sort_label, catalogOrder.label)
			setOnClickListener { showSortOptions() }
		}
		findViewById<MaterialButton>(R.id.monogatari_catalog_all).apply {
			MediaAccents.tintButton(requireContext(), "BOOK", this)
			setOnClickListener { search("All") }
		}
		findViewById<View>(R.id.monogatari_catalog_google)
			.setOnClickListener { search("Google Books") }
		findViewById<View>(R.id.monogatari_catalog_standard_ebooks)
			.setOnClickListener { search("Standard Ebooks") }
		findViewById<View>(R.id.monogatari_catalog_more_public)
			.setOnClickListener { showAdditionalCatalogs() }
		findViewById<RecyclerView>(R.id.monogatari_catalog_results).apply {
			layoutManager = GridLayoutManager(context,
				MonogatariChrome.columns(context))
			adapter = resultsAdapter
			if (isBottomTab) {
				// Room for the floating navigation bar, which is drawn over the main screen's content.
				setPadding(paddingLeft, paddingTop, paddingRight, (96 * resources.displayMetrics.density).toInt())
			}
		}
		findViewById<View>(R.id.monogatari_catalog_search_sources)
			.setOnClickListener { searchInstalledBookSources() }
		findViewById<View>(R.id.monogatari_catalog_search_all_sources).setOnClickListener {
			router.openMainSearch(MediaType.BOOK.name, query.text?.toString()?.trim()?.takeIf { it.isNotEmpty() })
		}
		query.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEARCH) {
				searchInstalledBookSources()
				true
			} else false
		}
		findViewById<View>(R.id.monogatari_catalog_gutenberg)
			.setOnClickListener { search("Gutenberg") }
		findViewById<View>(R.id.monogatari_catalog_openlibrary)
			.setOnClickListener { search("Open Library") }
		findViewById<View>(R.id.monogatari_catalog_archive)
			.setOnClickListener { search("Archive") }
		findViewById<View>(R.id.monogatari_catalog_wikisource)
			.setOnClickListener { search("Wikisource") }
		// Preserve query and provider when the phone rotates or Android recreates this screen.
		savedInstanceState?.getString("catalog_provider")?.takeIf { it.isNotBlank() }
			?.let(::search)
		if (savedInstanceState == null) {
			// Open with books on screen: repeat the last search, or show Standard Ebooks' newest releases,
			// which need no search term.
			val lastProvider = catalogPrefs.getString("last_provider", null)
			if (!lastProvider.isNullOrBlank() && query.text?.isNotBlank() == true) search(lastProvider)
			else search("Standard Ebooks")
		}
	}

	override fun onSaveInstanceState(outState: Bundle) {
		if (::query.isInitialized) outState.putString("catalog_query", query.text?.toString().orEmpty())
		outState.putString("catalog_provider", lastCatalogProvider)
		super.onSaveInstanceState(outState)
	}

	/** Uses the exact same multi-source search UI as manga, scoped only to book sources. */
	private fun showAdditionalCatalogs() {
		val providers = listOf(
			"Faded Page · Canadian public-domain books",
			"Gutenberg Australia · regional classics",
			"Global Grey · classics and historical books",
			"Baen Free Library · science fiction and fantasy",
			"StoryWeaver · illustrated CC BY stories (live catalog)",
			"FictionPress · original fiction (official website)",
			"FanFiction.net · fanfiction (official website)",
			"Bilibili · public videos (official website)",
		)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(getString(R.string.mono_books_more_entertainment_catalogs))
			.setItems(providers.toTypedArray()) { _, which ->
				val term = query.text?.toString()?.trim().orEmpty()
				when (which) {
					0 -> search("Faded Page")
					1 -> search("Gutenberg Australia")
					2 -> search("Global Grey")
					3 -> search("Baen Free Library")
					4 -> search("StoryWeaver")
					5 -> openFictionBrowser(OfficialMediaSites.Site.FICTIONPRESS, term)
					6 -> openFictionBrowser(OfficialMediaSites.Site.FANFICTION, term)
					7 -> openOfficialLink(OfficialMediaSites.searchUrl(OfficialMediaSites.Site.BILIBILI, term))
				}
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun searchInstalledBookSources() {
		val term = query.text?.toString()?.trim().orEmpty()
		if (term.isEmpty()) {
			query.error = getString(R.string.monogatari_catalog_search_empty)
			query.requestFocus()
			return
		}
		router.openMainSearch(MediaType.BOOK.name, term)
	}

	private fun saveOpds(url: String) {
		val parsed = runCatching { java.net.URI(url) }.getOrNull()
		if (parsed?.scheme !in setOf("http", "https") || parsed?.host.isNullOrBlank()) {
			showLines(listOf(getString(R.string.monogatari_catalog_opds_invalid)))
			return
		}
		val prefs = requireContext().getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
		val saved = prefs.getStringSet("urls", emptySet()).orEmpty().toMutableSet()
		saved.add(url)
		OpdsSourceRegistry.register(url)
		prefs.edit().putStringSet("urls", saved).apply()
		showSavedOpds()
	}

	private fun showSavedOpds() {
		val urls = requireContext().getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
			.getStringSet("urls", emptySet()).orEmpty().sorted()
		if (urls.isEmpty()) {
			showLines(listOf(getString(R.string.monogatari_catalog_opds_none)))
			return
		}
		resultsAdapter.show(urls.map { url ->
			CatalogRow(
				url, getString(R.string.monogatari_catalog_opds_saved),
				onOpen = { openOpds(url) },
				onLongPress = {
					val prefs = requireContext().getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
					val saved = prefs.getStringSet("urls", emptySet()).orEmpty().toMutableSet()
					saved.remove(url)
					OpdsSourceRegistry.unregister(url)
					prefs.edit().putStringSet("urls", saved).apply()
					showSavedOpds()
				},
			)
		})
	}

	private fun openOpds(url: String) {
		if (!url.startsWith("https://") && !url.startsWith("http://")) {
			showLines(listOf(getString(R.string.monogatari_catalog_opds_full_url)))
			return
		}
		showLines(listOf(getString(R.string.monogatari_catalog_opds_loading)))
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val entries = opds.load(url)
				if (entries.isEmpty()) showLines(listOf(getString(R.string.monogatari_catalog_opds_empty)))
				else resultsAdapter.show(entries.map { entry ->
					CatalogRow(entry.title, entry.author, onOpen = {
						// OPDS feeds are user-supplied: acquisition links open only as http(s) in a browser.
						if (entry.acquisition) {
							if (!ExternalLinks.open(requireContext(), entry.href)) catalogStatus.text = getString(R.string.link_cannot_open)
						}
						else openOpds(entry.href)
					})
				})
			} catch (cancel: kotlinx.coroutines.CancellationException) {
				throw cancel
			} catch (error: Exception) {
				showLines(listOf(getString(R.string.monogatari_catalog_opds_error, error.message.orEmpty())))
			}
		}
	}

	private data class ProviderResult(
		val source: String, val books: List<PublicBookCatalog.Book>,
		val error: String? = null,
	)

	private fun search(source: String) {
		val term = query.text?.toString()?.trim().orEmpty()
		if (term.isEmpty() && source !in setOf("Standard Ebooks", "Gutenberg Australia", "Global Grey", "Baen Free Library")) {
			query.error = getString(R.string.monogatari_catalog_search_enter_term)
			query.requestFocus()
			return
		}
		lastCatalogProvider = source
		catalogPrefs.edit().putString("last_query", term.take(200))
			.putString("last_provider", source).apply()
		catalogJob?.cancel()
		currentBooks = emptyList()
		currentProviderErrors = emptyList()
		resultsAdapter.showLoading()
		catalogStatus.visibility = View.VISIBLE
		catalogStatus.text = "Searching ${if (source == "All") "all catalogs" else source}..."
		catalogJob = viewLifecycleOwner.lifecycleScope.launch {
			val providers = if (source == "All")
				PublicCatalogProvider.entries.map { it.label }
			else listOf(source)
			try {
				// A slow, restricted or unreachable catalog cannot hide other providers' results.
				val fetched = supervisorScope {
					providers.map { provider ->
						async(Dispatchers.IO) {
							try { ProviderResult(provider, searchProvider(provider, term)) }
							catch (e: CancellationException) { throw e }
							catch (e: Exception) {
								ProviderResult(provider, emptyList(), e.message?.take(100) ?: "Unavailable")
							}
						}
					}.awaitAll()
				}
				currentBooks = fetched.flatMap { it.books }
				currentProviderErrors = fetched.filter { it.error != null }
					.map { "${it.source}: ${it.error}" }
				renderCatalogBooks()
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				catalogStatus.text = "Catalog search failed: ${e.message.orEmpty().take(120)}"
				resultsAdapter.show(emptyList())
			}
		}
	}

	private fun renderCatalogBooks() {
		val groups = BookCatalogMerger.sort(BookCatalogMerger.merge(currentBooks), catalogOrder)
		val errorSuffix = if (currentProviderErrors.isEmpty()) "" else
			"\nUnavailable: " + currentProviderErrors.joinToString(" · ")
		catalogStatus.text = "${groups.size} titles · ${currentBooks.size} provider results · ${catalogOrder.label}" +
			errorSuffix
		if (groups.isEmpty()) {
			resultsAdapter.show(listOf(CatalogRow(
				"No catalog titles matched.",
				if (currentProviderErrors.isEmpty()) "Try a different title or provider."
				else "Some providers could not be reached; try again or choose one."
			)))
			return
		}
		resultsAdapter.show(groups.map { group ->
			val preferredCatalog = catalogPrefs.getString("preferred:${group.identity}", null)
			val book = group.preferred(preferredCatalog)
			val description = listOfNotNull(
				book.author.takeIf { it.isNotBlank() }, group.providers,
				group.entries.mapNotNull { it.publicationYear }.minOrNull()?.toString(),
				book.language?.takeIf { it.isNotBlank() },
				book.format?.takeIf { it.isNotBlank() },
				book.genres.firstOrNull()?.takeIf { it.isNotBlank() },
				book.series?.let { getString(R.string.monogatari_catalog_series, it) },
				book.editionCount?.let { getString(R.string.monogatari_catalog_editions, it) },
				book.availability,
				book.downloadUrl?.let { "EPUB available" },
			).joinToString(" · ")
			CatalogRow(
				title = book.title, subtitle = description,
				coverUrl = book.coverUrl ?: group.entries.firstNotNullOfOrNull { it.coverUrl },
				onOpen = { openCatalogGroup(group) },
				onLongPress = { openCatalogGroup(group) },
				actionLabel = when {
					book.catalog == "Standard Ebooks" -> "Save & read EPUB"
					book.catalog == "StoryWeaver" -> "Read story"
					book.downloadUrl?.let { OfficialEpubDownloader.approved(book.catalog, it) } == true -> "Save & read EPUB"
					book.downloadUrl?.startsWith("https://") == true -> "Open EPUB externally"
					else -> "View listing"
				},
				onAction = {
					if (book.catalog == "Standard Ebooks") openStandardEpub(book)
					else if (book.catalog == "StoryWeaver") openStoryWeaverReader(book)
					else {
						val epub = book.downloadUrl?.takeIf { it.startsWith("https://") }
						if (epub != null && OfficialEpubDownloader.approved(book.catalog, epub)) {
							downloadAndRead(book, epub)
						} else openOfficialLink(epub ?: book.url)
					}
				},
			)
		})
	}

	private fun openCatalogGroup(group: BookCatalogMerger.Group) {
		if (group.entries.size == 1) {
			openBookListing(group.entries.first())
			return
		}
		val preferred = catalogPrefs.getString("preferred:${group.identity}", null)
		MaterialAlertDialogBuilder(requireContext())
			.setTitle("Choose a source for ${group.primary.title}")
			.setItems(group.entries.map {
				"${if (it.catalog == preferred) "★ " else ""}${it.catalog} · ${it.format ?: "Source listing"}"
			}.toTypedArray()) { _, position ->
				val selected = group.entries[position]
				catalogPrefs.edit().putString("preferred:${group.identity}", selected.catalog).apply()
				renderCatalogBooks()
				openBookListing(selected)
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun openStandardEpub(book: PublicBookCatalog.Book) {
		if (!StandardEbooksCatalog.officialPage(book.url)) {
			catalogStatus.text = getString(R.string.mono_books_invalid_standard_ebooks_link)
			return
		}
		catalogStatus.text = "Finding the compatible EPUB for ${book.title}..."
		viewLifecycleOwner.lifecycleScope.launch {
			// Resolve only after a user click; no bulk scraping or invented download links.
			val link = withContext(Dispatchers.IO) {
				runCatching { catalog.resolveStandardEbooksEpub(book.url) }.getOrNull()
			}
			if (link == null) {
				catalogStatus.text = getString(R.string.monogatari_epub_edition_missing)
				openOfficialLink(book.url)
			} else downloadAndRead(book, link)
		}
	}

	private fun openBookListing(book: PublicBookCatalog.Book) {
		if (book.catalog == "StoryWeaver") {
			MaterialAlertDialogBuilder(requireContext()).setTitle(book.title)
				.setMessage(bookMetadataSummary(book))
				.setPositiveButton(R.string.storyweaver_read_story) { _, _ -> openStoryWeaverReader(book) }
				.setNeutralButton(R.string.storyweaver_open_browser) { _, _ -> openOfficialLink(book.url) }
				.setNegativeButton(android.R.string.cancel, null).show()
			return
		}
		if (book.catalog == "Standard Ebooks") {
			MaterialAlertDialogBuilder(requireContext()).setTitle(book.title)
				.setMessage(bookMetadataSummary(book))
				.setItems(arrayOf("View official listing", "Save EPUB and read offline")) { _, which ->
					if (which == 1) openStandardEpub(book) else openOfficialLink(book.url)
				}
				.setNegativeButton(android.R.string.cancel, null).show()
			return
		}
		val epub = book.downloadUrl?.takeIf { it.startsWith("https://") }
		val approvedEpub = epub?.takeIf { OfficialEpubDownloader.approved(book.catalog, it) }
		val actions = when {
			approvedEpub != null -> arrayOf("View ${book.catalog} listing",
				"Save EPUB and read offline", "Open EPUB in browser")
			epub != null -> arrayOf("View ${book.catalog} listing", "Open EPUB in browser")
			else -> arrayOf("View ${book.catalog} listing")
		}
		MaterialAlertDialogBuilder(requireContext()).setTitle(book.title)
			.setMessage(bookMetadataSummary(book))
			.setItems(actions) { _, which ->
				when (actions[which]) {
					"Save EPUB and read offline" -> approvedEpub?.let { downloadAndRead(book, it) }
					"Open EPUB in browser" -> openOfficialLink(epub ?: book.url)
					else -> openOfficialLink(book.url)
				}
			}.setNegativeButton(android.R.string.cancel, null).show()
	}

	/** A clear series/edition/access summary without implying unrestricted access or a guaranteed loan. */
	private fun bookMetadataSummary(book: PublicBookCatalog.Book): String = listOfNotNull(
		book.author.takeIf { it.isNotBlank() }, book.catalog,
		book.publicationYear?.toString(), book.language,
		book.series?.let { getString(R.string.monogatari_catalog_series, it) },
		book.editionCount?.let { getString(R.string.monogatari_catalog_editions, it) },
		book.format, book.availability, book.attribution,
	).joinToString("\n")

	private fun downloadAndRead(book: PublicBookCatalog.Book, link: String) {
		if (!OfficialEpubDownloader.approved(book.catalog, link)) {
			catalogStatus.text = getString(R.string.monogatari_epub_unapproved)
			return
		}
		catalogStatus.text = getString(R.string.monogatari_epub_downloading, book.title)
		viewLifecycleOwner.lifecycleScope.launch {
			try {
				val library = File(requireContext().filesDir, "imported-books")
				val file = OfficialEpubDownloader.save(epubClient, library, book.title, book.catalog, link)
				catalogStatus.text = getString(R.string.monogatari_epub_saved, book.title)
				startActivity(BookShelf.readerIntent(requireContext(), file))
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Exception) {
				catalogStatus.text = getString(R.string.monogatari_epub_download_error,
					error.message.orEmpty().take(130))
			}
		}
	}

	private fun openStoryWeaverReader(book: PublicBookCatalog.Book) {
		if (!StoryWeaverCatalog.officialStoryUrl(book.url)) {
			catalogStatus.text = getString(R.string.storyweaver_invalid_link)
			return
		}
		startActivity(Intent(requireContext(), StoryWeaverReaderActivity::class.java)
			.putExtra(StoryWeaverReaderActivity.EXTRA_URL, book.url)
			.putExtra(StoryWeaverReaderActivity.EXTRA_CREDIT, book.attribution))
	}

	/** These sites do not expose a permitted public chapter API: always use their own search website. */
	private fun openFictionBrowser(site: OfficialMediaSites.Site, term: String) {
		val url = OfficialMediaSites.searchUrl(site, term)
		if (OfficialMediaSites.fictionSite(url) != site) return
		startActivity(Intent(requireContext(), OfficialFictionBrowserActivity::class.java)
			.putExtra(OfficialFictionBrowserActivity.EXTRA_URL, url))
	}

	private fun openOfficialLink(url: String) {
		if (!url.startsWith("https://")) {
			catalogStatus.text = getString(R.string.mono_books_only_secure_official_catalog_links_can)
			return
		}
		runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
			.onFailure { catalogStatus.text = getString(R.string.mono_books_no_browser_is_available_to_open) }
	}

	private suspend fun searchProvider(source: String, term: String): List<PublicBookCatalog.Book> =
		PublicCatalogProvider.fromLabel(source)?.search(catalog, term).orEmpty()

	private fun showSortOptions() {
		val options = BookCatalogSorter.Order.entries
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(getString(R.string.mono_books_sort_catalog_results))
			.setSingleChoiceItems(
				options.map { it.label }.toTypedArray(), options.indexOf(catalogOrder)
			) { dialog, selected ->
				catalogOrder = options[selected]
				requireContext().getSharedPreferences("book_catalog_ui", Context.MODE_PRIVATE)
					.edit().putString("sort", catalogOrder.name).apply()
				findViewById<Chip>(R.id.monogatari_catalog_sort)
					.text = getString(R.string.monogatari_catalog_sort_label, catalogOrder.label)
				if (currentBooks.isNotEmpty()) renderCatalogBooks()
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null).show()
	}

	private fun showLocalBooks(filter: String = "") {
		// Saved remote titles and imported books now share the same searchable shelf.
		startActivity(Intent(requireContext(), UnifiedLibraryActivity::class.java)
			.putExtra(UnifiedLibraryActivity.EXTRA_MEDIA_KIND, MediaType.BOOK.name)
			.putExtra(UnifiedLibraryActivity.EXTRA_FILTER, filter))
	}

	private fun showLines(lines: List<String>) {
		resultsAdapter.show(lines.map { CatalogRow(it) })
	}

	private companion object {
		const val MENU_IMPORT = 7204
		const val MENU_MY_BOOKS = 7205
		const val MENU_FIND_IMPORTED = 7206
		const val MENU_OPDS_OPEN = 7207
		const val MENU_OPDS_SAVE = 7208
		const val MENU_OPDS_SAVED = 7209
	}
}
