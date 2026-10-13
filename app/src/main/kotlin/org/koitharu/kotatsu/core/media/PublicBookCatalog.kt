package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject

/** Public-domain and library catalog discovery; availability is never assumed. */
class PublicBookCatalog @Inject constructor(@BaseHttpClient private val client: OkHttpClient) {
    data class Book(
        val title: String,
        val author: String,
        val catalog: String,
        val url: String,
        val downloadUrl: String? = null,
    )

    suspend fun searchGutenberg(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val json = fetch("https://gutendex.com/books/?search=${encode(query)}&page=${page.coerceAtLeast(1)}")
        val items = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val obj = items.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
            val formats = obj.optJSONObject("formats")
            val epub = formats?.optString("application/epub+zip")?.takeIf { it.startsWith("https://") }
            Book(obj.optString("title"), obj.optJSONArray("authors")?.optJSONObject(0)?.optString("name").orEmpty(),
                "Project Gutenberg", "https://www.gutenberg.org/ebooks/$id", epub)
        }
    }

    suspend fun searchOpenLibrary(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val json = fetch("https://openlibrary.org/search.json?q=${encode(query)}&page=${page.coerceAtLeast(1)}&limit=30")
        val items = json.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val obj = items.optJSONObject(i) ?: return@mapNotNull null
            val key = obj.optString("key").takeIf { it.startsWith("/works/") } ?: return@mapNotNull null
            Book(obj.optString("title"), obj.optJSONArray("author_name")?.optString(0).orEmpty(),
                "Open Library", "https://openlibrary.org$key")
        }
    }

    suspend fun searchInternetArchive(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val expression = "(${query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()}) AND mediatype:texts"
        val json = fetch("https://archive.org/advancedsearch.php?q=${encode(expression)}&fl[]=identifier&fl[]=title&fl[]=creator&rows=30&page=${page.coerceAtLeast(1)}&output=json")
        val items = json.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val obj = items.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Book(obj.optString("title"), obj.optString("creator"), "Internet Archive",
                "https://archive.org/details/${encode(id)}")
        }
    }

    /** Official public Wikisource search API, not a scraper or a DRM workaround. */
    suspend fun searchWikisource(query: String): List<Book> = withContext(Dispatchers.IO) {
        val json = fetch("https://en.wikisource.org/w/api.php?action=query&list=search&srnamespace=0&srlimit=30&format=json&srsearch=${encode(query)}")
        val items = json.optJSONObject("query")?.optJSONArray("search") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val entry = items.optJSONObject(i) ?: return@mapNotNull null
            val title = entry.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Book(title, "Wikisource community", "Wikisource",
                "https://en.wikisource.org/wiki/${encode(title.replace(' ', '_')).replace("+", "%20")}")
        }
    }

    /** Internet Archive's public video metadata index; open the official playback page. */
    suspend fun searchInternetArchiveVideos(query: String): List<Book> = withContext(Dispatchers.IO) {
        val safe = query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
        if (safe.isBlank()) return@withContext emptyList()
        val expression = "($safe) AND mediatype:movies"
        val json = fetch("https://archive.org/advancedsearch.php?q=${encode(expression)}&fl[]=identifier&fl[]=title&fl[]=creator&rows=25&output=json")
        val items = json.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val entry = items.optJSONObject(i) ?: return@mapNotNull null
            val id = entry.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Book(entry.optString("title").ifBlank { id }, entry.optString("creator"),
                "Internet Archive Video", "https://archive.org/details/${encode(id)}")
        }
    }

    private fun fetch(url: String): JSONObject {
        val request = Request.Builder().url(url).header("User-Agent", "Monogatari/1.0 (book catalog)").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Book catalog HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty catalog response"))
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
}
