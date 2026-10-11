package org.koitharu.kotatsu.core.media

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
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
                        if (name.substringAfterLast('.', "").lowercase() !in setOf("epub", "pdf", "txt", "html", "htm")) {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Monogatari Books"
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 12) }
        query = EditText(this).apply { hint = "Search books by title or author"; setSingleLine(true) }
        root.addView(query)
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Gutenberg", "Open Library", "Archive").forEach { source ->
            actions.addView(Button(this).apply {
                text = source
                setOnClickListener { search(source) }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        root.addView(actions)
        root.addView(Button(this).apply {
            text = "Open OPDS catalog URL"
            setOnClickListener { openOpds(query.text.toString().trim()) }
        })
        root.addView(Button(this).apply {
            text = "Import local ebooks"
            setOnClickListener { picker.launch(arrayOf("*/*")) }
        })
        root.addView(Button(this).apply {
            text = "My imported books"
            setOnClickListener { showLocalBooks() }
        })
        root.addView(Button(this).apply {
            text = "Search imported books"
            setOnClickListener { showLocalBooks(query.text.toString().trim()) }
        })
        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(ScrollView(this).apply { addView(results) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
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
                    results.addView(Button(this@BookLibraryActivity).apply {
                        text = "${entry.title} - ${entry.author}"
                        isAllCaps = false
                        setOnClickListener {
                            if (entry.acquisition) startActivity(Intent(Intent.ACTION_VIEW, entry.href.toUri()))
                            else openOpds(entry.href)
                        }
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
                    results.addView(Button(this@BookLibraryActivity).apply {
                        text = "${book.title}\n${book.author} · ${book.catalog}"
                        isAllCaps = false
                        setOnClickListener { startActivity(Intent(Intent.ACTION_VIEW, book.url.toUri())) }
                    })
                }
            } catch (e: Exception) { showLines(listOf("Search failed: ${e.message}")) }
        }
    }

    private fun showLocalBooks(filter: String = "") {
        results.removeAllViews()
        val books = File(filesDir, "imported-books").listFiles().orEmpty()
            .filter { it.isFile && it.name.contains(filter, ignoreCase = true) }
            .sortedBy { it.name }
        if (books.isEmpty()) showLines(listOf("No imported books yet."))
        books.forEach { file ->
            results.addView(Button(this).apply {
                text = file.name.substringAfter('-')
                isAllCaps = false
                setOnClickListener {
                    val type = when (file.extension.lowercase()) {
                        "pdf" -> "application/pdf"
                        "epub" -> "application/epub+zip"
                        "html", "htm" -> "text/html"
                        else -> "text/plain"
                    }
                    if (type == "application/epub+zip") {
                        startActivity(Intent(this@BookLibraryActivity, LocalEpubReaderActivity::class.java)
                            .putExtra("local_book_file", file.absolutePath))
                        return@setOnClickListener
                    }
                    if (type == "application/pdf") {
                        startActivity(Intent(this@BookLibraryActivity, LocalPdfReaderActivity::class.java)
                            .putExtra("local_book_file", file.absolutePath))
                        return@setOnClickListener
                    }
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@BookLibraryActivity, "${packageName}.files", file)
                    if (type == "text/plain" || type == "text/html") {
                        startActivity(Intent(this@BookLibraryActivity, LocalTextReaderActivity::class.java)
                            .putExtra(LocalTextReaderActivity.EXTRA_FILE, file.absolutePath))
                        return@setOnClickListener
                    }
                    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, type)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    try { startActivity(intent) } catch (e: Exception) {
                        showLines(listOf("No compatible reader installed: ${e.message}"))
                    }
                }
            })
        }
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
