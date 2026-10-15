package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.MascotForm
import android.widget.ImageView
import android.content.Intent
import android.view.DragEvent
import androidx.core.app.ActivityCompat
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import org.koitharu.kotatsu.core.qa.CsvExportFormatter
import android.view.inputmethod.EditorInfo
import org.koitharu.kotatsu.builtin.opds.OpdsSourceRegistry
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.multi.SearchActivity
import android.widget.EditText
import android.widget.TextView
import android.view.View
import coil3.ImageLoader
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.koitharu.kotatsu.R
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Initial book catalog and local ebook shelf; no DRM or lending bypass. */
@AndroidEntryPoint
class BookLibraryActivity : AppCompatActivity() {
    @Inject lateinit var catalog: PublicBookCatalog
    @Inject lateinit var imageLoader: ImageLoader
    @Inject lateinit var opds: OpdsCatalog
    private val resultsAdapter = CatalogResultsAdapter()
    private lateinit var query: EditText
    private lateinit var catalogStatus: TextView
    private var currentBooks = emptyList<PublicBookCatalog.Book>()
    private var currentProviderErrors = emptyList<String>()
    private var catalogJob: Job? = null
    private var lastCatalogProvider: String? = null
    private var catalogOrder = BookCatalogSorter.Order.RELEVANCE
    private val catalogPrefs by lazy { getSharedPreferences("book_catalog_ui", MODE_PRIVATE) }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importBooks(uris)
    }

    /** Copies picked or drag-and-dropped files into the local library. */
    private fun importBooks(uris: List<Uri>) {
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                val dir = File(filesDir, "imported-books").apply { mkdirs() }
                uris.map { uri ->
                    try {
                        val name = queryDisplayName(uri)
                        if (name.substringAfterLast('.', "").lowercase() !in setOf("epub", "pdf", "txt", "text", "md", "markdown", "html", "htm", "xhtml")) {
                            return@map getString(R.string.monogatari_catalog_import_bad_format, name)
                        }
                        val safeName = name.replace(Regex("[^a-zA-Z0-9._ -]"), "_").take(120)
                        val dest = File.createTempFile("book-", "-$safeName", dir)
                        var completed = false
                        try {
                            contentResolver.openInputStream(uri)?.use { input ->
                                dest.outputStream().use { input.copyTo(it) }
                            } ?: error(getString(R.string.monogatari_catalog_import_read_failed))
                            require(dest.length() > 0) { "The selected book is empty" }
                            completed = true
                        } finally {
                            // Failed or cancelled copies must never appear on the user's book shelf.
                            if (!completed) dest.delete()
                        }
                        getString(R.string.monogatari_catalog_imported, name)
                    } catch (cancel: CancellationException) { throw cancel }
                    catch (e: Exception) { getString(R.string.monogatari_catalog_import_failed, uri.lastPathSegment.orEmpty(), e.message.orEmpty()) }
                }
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
                    val permissions = ActivityCompat.requestDragAndDropPermissions(this, event)
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
        if (uri != null) lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val books = BookShelf.list(this@BookLibraryActivity)
                    val csv = CsvExportFormatter.encode(listOf("Title", "Format", "Progress"), books.map {
                        listOf(BookShelf.title(it), it.extension.lowercase(), BookShelf.progress(this@BookLibraryActivity, it))
                    })
                    contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) }
                        ?: error(getString(R.string.monogatari_catalog_export_destination_error))
                    getString(R.string.monogatari_catalog_exported, books.size)
                }.getOrElse { getString(R.string.monogatari_catalog_export_failed, it.message.orEmpty()) }
            }
            showLines(listOf(result))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 7203, 0, R.string.monogatari_catalog_menu_export)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == 7203) {
            exportPicker.launch("monogatari-books.csv")
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.monogatari_catalog_title)
        val content = layoutInflater.inflate(R.layout.activity_monogatari_book_catalog, null)
        setMonogatariContent(content, MonogatariChrome.LIST_MAX_WIDTH_DP)
        Mascot.bind(findViewById<ImageView>(R.id.monogatari_catalog_mascot), MascotForm.BOOK)
        enableFileDrop(content)
        query = findViewById(R.id.monogatari_catalog_query)
        query.setText(savedInstanceState?.getString("catalog_query")
            ?: catalogPrefs.getString("last_query", "").orEmpty())
        catalogStatus = findViewById(R.id.monogatari_catalog_status)
        resultsAdapter.imageLoader = imageLoader
        catalogOrder = runCatching {
            BookCatalogSorter.Order.valueOf(
                getSharedPreferences("book_catalog_ui", MODE_PRIVATE)
                    .getString("sort", BookCatalogSorter.Order.RELEVANCE.name)!!
            )
        }.getOrDefault(BookCatalogSorter.Order.RELEVANCE)
        findViewById<MaterialButton>(R.id.monogatari_catalog_sort).apply {
            text = "Sort: ${catalogOrder.label}"
            setOnClickListener { showSortOptions() }
        }
        findViewById<MaterialButton>(R.id.monogatari_catalog_all)
            .setOnClickListener { search("All") }
        findViewById<MaterialButton>(R.id.monogatari_catalog_google)
            .setOnClickListener { search("Google Books") }
        findViewById<MaterialButton>(R.id.monogatari_catalog_standard_ebooks)
            .setOnClickListener { search("Standard Ebooks") }
        findViewById<MaterialButton>(R.id.monogatari_catalog_more_public)
            .setOnClickListener { showAdditionalCatalogs() }
        findViewById<RecyclerView>(R.id.monogatari_catalog_results).apply {
            layoutManager = GridLayoutManager(this@BookLibraryActivity,
                MonogatariChrome.columns(context))
            adapter = resultsAdapter
        }
        findViewById<MaterialButton>(R.id.monogatari_catalog_search_sources).apply {
            MediaAccents.tintButton(this@BookLibraryActivity, "BOOK", this)
            setOnClickListener { searchInstalledBookSources() }
        }
        query.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchInstalledBookSources()
                true
            } else false
        }
        findViewById<MaterialButton>(R.id.monogatari_catalog_gutenberg)
            .setOnClickListener { search("Gutenberg") }
        findViewById<MaterialButton>(R.id.monogatari_catalog_openlibrary)
            .setOnClickListener { search("Open Library") }
        findViewById<MaterialButton>(R.id.monogatari_catalog_archive)
            .setOnClickListener { search("Archive") }
        findViewById<MaterialButton>(R.id.monogatari_catalog_wikisource)
            .setOnClickListener { search("Wikisource") }
        findViewById<MaterialButton>(R.id.monogatari_catalog_opds_open)
            .setOnClickListener { openOpds(query.text.toString().trim()) }
        findViewById<MaterialButton>(R.id.monogatari_catalog_opds_save)
            .setOnClickListener { saveOpds(query.text.toString().trim()) }
        findViewById<MaterialButton>(R.id.monogatari_catalog_opds_saved)
            .setOnClickListener { showSavedOpds() }
        findViewById<MaterialButton>(R.id.monogatari_catalog_import)
            .setOnClickListener { picker.launch(arrayOf("*/*")) }
        findViewById<MaterialButton>(R.id.monogatari_catalog_mybooks)
            .setOnClickListener { showLocalBooks() }
        findViewById<MaterialButton>(R.id.monogatari_catalog_filter)
            .setOnClickListener { showLocalBooks(query.text.toString().trim()) }
        // Preserve query and provider when the phone rotates or Android recreates this Activity.
        savedInstanceState?.getString("catalog_provider")?.takeIf { it.isNotBlank() }
            ?.let(::search)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("catalog_query", query.text?.toString().orEmpty())
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
            "StoryWeaver · illustrated stories (official website)",
            "FictionPress · original fiction (official website)",
            "FanFiction.net · fanfiction (official website)",
            "Bilibili · public videos (official website)",
        )
        MaterialAlertDialogBuilder(this)
            .setTitle("More entertainment catalogs")
            .setItems(providers.toTypedArray()) { _, which ->
                val term = query.text?.toString()?.trim().orEmpty()
                when (which) {
                    0 -> search("Faded Page")
                    1 -> search("Gutenberg Australia")
                    2 -> search("Global Grey")
                    3 -> search("Baen Free Library")
                    4 -> openOfficialLink(OfficialMediaSites.searchUrl(OfficialMediaSites.Site.STORYWEAVER, term))
                    5 -> openOfficialLink(OfficialMediaSites.searchUrl(OfficialMediaSites.Site.FICTIONPRESS, term))
                    6 -> openOfficialLink(OfficialMediaSites.searchUrl(OfficialMediaSites.Site.FANFICTION, term))
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
        startActivity(Intent(this, SearchActivity::class.java)
            .putExtra(AppRouter.KEY_QUERY, term)
            .putExtra(AppRouter.KEY_KIND, SearchKind.SIMPLE)
            .putExtra(SearchActivity.EXTRA_MEDIA_TYPE, MediaType.BOOK.name))
    }

    private fun saveOpds(url: String) {
        val parsed = runCatching { java.net.URI(url) }.getOrNull()
        if (parsed?.scheme !in setOf("http", "https") || parsed?.host.isNullOrBlank()) {
            showLines(listOf(getString(R.string.monogatari_catalog_opds_invalid)))
            return
        }
        val prefs = getSharedPreferences("opds_catalogs", MODE_PRIVATE)
        val saved = prefs.getStringSet("urls", emptySet()).orEmpty().toMutableSet()
        saved.add(url)
        OpdsSourceRegistry.register(url)
        prefs.edit().putStringSet("urls", saved).apply()
        showSavedOpds()
    }

    private fun showSavedOpds() {
        val urls = getSharedPreferences("opds_catalogs", MODE_PRIVATE)
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
                    val prefs = getSharedPreferences("opds_catalogs", MODE_PRIVATE)
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
        lifecycleScope.launch {
            try {
                val entries = opds.load(url)
                if (entries.isEmpty()) showLines(listOf(getString(R.string.monogatari_catalog_opds_empty)))
                else resultsAdapter.show(entries.map { entry ->
                    CatalogRow(entry.title, entry.author, onOpen = {
                        if (entry.acquisition) startActivity(Intent(Intent.ACTION_VIEW, entry.href.toUri()))
                        else openOpds(entry.href)
                    })
                })
            } catch (error: Exception) { showLines(listOf(getString(R.string.monogatari_catalog_opds_error, error.message.orEmpty()))) }
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
        catalogJob = lifecycleScope.launch {
            val providers = if (source == "All")
                listOf("Gutenberg", "Open Library", "Archive", "Wikisource", "Google Books", "Standard Ebooks",
                    "Faded Page", "Gutenberg Australia", "Global Grey", "Baen Free Library")
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
                book.downloadUrl?.let { "EPUB available" },
            ).joinToString(" · ")
            CatalogRow(
                title = book.title, subtitle = description,
                coverUrl = book.coverUrl ?: group.entries.firstNotNullOfOrNull { it.coverUrl },
                onOpen = { openCatalogGroup(group) },
                onLongPress = { openCatalogGroup(group) },
                actionLabel = when {
                    book.catalog == "Standard Ebooks" -> "Get EPUB"
                    book.downloadUrl?.startsWith("https://") == true -> "Open EPUB"
                    else -> "View listing"
                },
                onAction = {
                    if (book.catalog == "Standard Ebooks") openStandardEpub(book)
                    else {
                        val epub = book.downloadUrl?.takeIf { it.startsWith("https://") }
                        openOfficialLink(epub ?: book.url)
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
        MaterialAlertDialogBuilder(this)
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
            catalogStatus.text = "Invalid Standard Ebooks link."
            return
        }
        catalogStatus.text = "Finding the compatible EPUB for ${book.title}..."
        lifecycleScope.launch {
            // Resolve only after a user click; no bulk scraping or invented download links.
            val link = withContext(Dispatchers.IO) {
                runCatching { catalog.resolveStandardEbooksEpub(book.url) }.getOrNull()
            }
            if (link == null) catalogStatus.text =
                "Compatible EPUB unavailable. Opening the official listing instead."
            openOfficialLink(link ?: book.url)
        }
    }

    private fun openBookListing(book: PublicBookCatalog.Book) {
        if (book.catalog == "Standard Ebooks") {
            MaterialAlertDialogBuilder(this).setTitle(book.title)
                .setItems(arrayOf("View official listing", "Open compatible EPUB")) { _, which ->
                    if (which == 1) openStandardEpub(book) else openOfficialLink(book.url)
                }
                .setNegativeButton(android.R.string.cancel, null).show()
            return
        }
        val epub = book.downloadUrl?.takeIf { it.startsWith("https://") }
        if (epub == null) {
            openOfficialLink(book.url)
            return
        }
        MaterialAlertDialogBuilder(this).setTitle(book.title)
            .setItems(arrayOf("View ${book.catalog} listing", "Open available EPUB")) { _, which ->
                openOfficialLink(if (which == 1) epub else book.url)
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun openOfficialLink(url: String) {
        if (!url.startsWith("https://")) {
            catalogStatus.text = "Only secure official catalog links can be opened."
            return
        }
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
            .onFailure { catalogStatus.text = "No browser is available to open that catalog." }
    }

    private suspend fun searchProvider(source: String, term: String): List<PublicBookCatalog.Book> =
        when (source) {
            "Gutenberg" -> catalog.searchGutenberg(term)
            "Open Library" -> catalog.searchOpenLibrary(term)
            "Archive" -> catalog.searchInternetArchive(term)
            "Wikisource" -> catalog.searchWikisource(term)
            "Google Books" -> catalog.searchGoogleBooks(term)
            "Standard Ebooks" -> catalog.searchStandardEbooks(term)
            "Faded Page" -> catalog.searchFadedPage(term)
            "Gutenberg Australia" -> catalog.searchGutenbergAustralia(term)
            "Global Grey" -> catalog.searchGlobalGrey(term)
            "Baen Free Library" -> catalog.searchBaenFreeLibrary(term)
            else -> emptyList()
        }

    private fun showSortOptions() {
        val options = BookCatalogSorter.Order.entries
        MaterialAlertDialogBuilder(this)
            .setTitle("Sort catalog results")
            .setSingleChoiceItems(
                options.map { it.label }.toTypedArray(), options.indexOf(catalogOrder)
            ) { dialog, selected ->
                catalogOrder = options[selected]
                getSharedPreferences("book_catalog_ui", MODE_PRIVATE)
                    .edit().putString("sort", catalogOrder.name).apply()
                findViewById<MaterialButton>(R.id.monogatari_catalog_sort)
                    .text = "Sort: ${catalogOrder.label}"
                if (currentBooks.isNotEmpty()) renderCatalogBooks()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun showLocalBooks(filter: String = "") {
        // Saved remote titles and imported books now share the same searchable shelf.
        startActivity(Intent(this, UnifiedLibraryActivity::class.java)
            .putExtra(UnifiedLibraryActivity.EXTRA_MEDIA_KIND, MediaType.BOOK.name)
            .putExtra(UnifiedLibraryActivity.EXTRA_FILTER, filter))
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0) ?: "book"
        }
        return "book"
    }

    private fun showLines(lines: List<String>) {
        resultsAdapter.show(lines.map { CatalogRow(it) })
    }
}
