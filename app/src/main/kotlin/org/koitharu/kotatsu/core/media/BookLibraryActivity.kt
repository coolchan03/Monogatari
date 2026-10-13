package org.koitharu.kotatsu.core.media

import android.content.Intent
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
import android.view.LayoutInflater
import com.google.android.material.card.MaterialCardView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import org.koitharu.kotatsu.R
import androidx.core.net.toUri
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/** Initial book catalog and local ebook shelf; no DRM or lending bypass. */
@AndroidEntryPoint
class BookLibraryActivity : AppCompatActivity() {
    @Inject lateinit var catalog: PublicBookCatalog
    @Inject lateinit var opds: OpdsCatalog
    private lateinit var results: LinearLayout
    private lateinit var query: EditText
    private val picker = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        lifecycleScope.launch {
            val report = withContext(Dispatchers.IO) {
                val dir = File(filesDir, "imported-books").apply { mkdirs() }
                uris.map { uri ->
                    try {
                        val name = queryDisplayName(uri)
                        if (name.substringAfterLast('.', "").lowercase() !in setOf("epub", "pdf", "txt", "text", "md", "markdown", "html", "htm", "xhtml")) {
                            return@map "$name: unsupported format"
                        }
                        val dest = File.createTempFile("book-", "-${name.replace(Regex("[^a-zA-Z0-9._ -]"), "_")}", dir)
                        contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
                            ?: error("Cannot open file")
                        "$name: imported"
                    } catch (e: Exception) { "${uri.lastPathSegment}: ${e.message}" }
                }
            }
            showLines(report)
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
                        ?: error("Unable to open export destination")
                    "Exported ${books.size} books"
                }.getOrElse { "Export failed: ${it.message}" }
            }
            showLines(listOf(result))
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 7203, 0, "Export my books (CSV)")
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
        setContentView(R.layout.activity_monogatari_book_catalog)
        query = findViewById(R.id.monogatari_catalog_query)
        results = findViewById(R.id.monogatari_catalog_results)
        findViewById<MaterialButton>(R.id.monogatari_catalog_search_sources)
            .setOnClickListener { searchInstalledBookSources() }
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
    }

    /** Uses the exact same multi-source search UI as manga, scoped only to book sources. */
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
            showLines(listOf("Enter a valid HTTP(S) OPDS URL."))
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
        results.removeAllViews()
        val urls = getSharedPreferences("opds_catalogs", MODE_PRIVATE)
            .getStringSet("urls", emptySet()).orEmpty().sorted()
        if (urls.isEmpty()) showLines(listOf("No saved OPDS catalogs."))
        urls.forEach { url ->
            results.addView(catalogCard(url, getString(R.string.monogatari_catalog_opds_saved)) {
                openOpds(url)
            }.apply {
                setOnLongClickListener {
                    val prefs = getSharedPreferences("opds_catalogs", MODE_PRIVATE)
                    val saved = prefs.getStringSet("urls", emptySet()).orEmpty().toMutableSet()
                    saved.remove(url)
                    OpdsSourceRegistry.unregister(url)
                    prefs.edit().putStringSet("urls", saved).apply()
                    showSavedOpds()
                    true
                }
            })
        }
    }

    private fun openOpds(url: String) {
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            showLines(listOf("Enter a full HTTP(S) OPDS catalog URL in the search box."))
            return
        }
        showLines(listOf("Loading OPDS catalog..."))
        lifecycleScope.launch {
            try {
                val entries = opds.load(url)
                results.removeAllViews()
                if (entries.isEmpty()) showLines(listOf("No OPDS entries found."))
                entries.forEach { entry ->
                    results.addView(catalogCard(entry.title, entry.author) {
                        if (entry.acquisition) startActivity(Intent(Intent.ACTION_VIEW, entry.href.toUri()))
                        else openOpds(entry.href)
                    })
                }
            } catch (error: Exception) { showLines(listOf("OPDS error: ${error.message}")) }
        }
    }

    private fun search(source: String) {
        val term = query.text.toString().trim()
        if (term.isEmpty()) { showLines(listOf("Enter a search term.")); return }
        showLines(listOf("Searching $source..."))
        lifecycleScope.launch {
            try {
                val books = when (source) {
                    "Gutenberg" -> catalog.searchGutenberg(term)
                    "Open Library" -> catalog.searchOpenLibrary(term)
                    else -> catalog.searchInternetArchive(term)
                }
                results.removeAllViews()
                if (books.isEmpty()) showLines(listOf("No matches."))
                books.forEach { book ->
                    results.addView(catalogCard(book.title, "${book.author} · ${book.catalog}") {
                        startActivity(Intent(Intent.ACTION_VIEW, book.url.toUri()))
                    })
                }
            } catch (e: Exception) { showLines(listOf("Search failed: ${e.message}")) }
        }
    }

    private fun showLocalBooks(filter: String = "") {
        // Saved remote titles and imported books now share the same searchable shelf.
        startActivity(Intent(this, UnifiedLibraryActivity::class.java)
            .putExtra(UnifiedLibraryActivity.EXTRA_MEDIA_KIND, MediaType.BOOK.name)
            .putExtra(UnifiedLibraryActivity.EXTRA_FILTER, filter))
    }

    private fun catalogCard(title: String, subtitle: String, onOpen: () -> Unit): MaterialCardView {
        val card = LayoutInflater.from(this).inflate(R.layout.item_monogatari_catalog, results, false)
            as MaterialCardView
        card.findViewById<TextView>(R.id.monogatari_catalog_item_title).text = title
        card.findViewById<TextView>(R.id.monogatari_catalog_item_summary).text = subtitle
        card.contentDescription = "$title, $subtitle"
        card.setOnClickListener { onOpen() }
        return card
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0) ?: "book"
        }
        return "book"
    }

    private fun showLines(lines: List<String>) {
        results.removeAllViews()
        lines.forEach { line -> results.addView(TextView(this).apply { text = line; textSize = 16f; setPadding(8, 12, 8, 12) }) }
    }
}
