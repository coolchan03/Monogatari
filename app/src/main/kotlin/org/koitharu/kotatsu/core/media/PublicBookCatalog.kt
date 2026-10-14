package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.qa.NewsFeedParser
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
        val coverUrl: String? = null,
        val publicationYear: Int? = null,
        val language: String? = null,
        val format: String? = null,
    )

    suspend fun searchGutenberg(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val json = fetch("https://gutendex.com/books/?search=${encode(query)}&page=${page.coerceAtLeast(1)}")
        val items = json.optJSONArray("results") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val obj = items.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optInt("id").takeIf { it > 0 } ?: return@mapNotNull null
            val formats = obj.optJSONObject("formats")
            val epub = formats?.optString("application/epub+zip")?.takeIf { it.startsWith("https://") }
            val image = formats?.optString("image/jpeg")?.takeIf { it.startsWith("https://") }
            Book(obj.optString("title"), obj.optJSONArray("authors")?.optJSONObject(0)?.optString("name").orEmpty(),
                "Project Gutenberg", "https://www.gutenberg.org/ebooks/$id", epub,
                coverUrl = image, language = obj.optJSONArray("languages")?.optString(0),
                format = if (epub != null) "EPUB" else "Online book")
        }
    }

    suspend fun searchOpenLibrary(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val json = fetch("https://openlibrary.org/search.json?q=${encode(query)}&page=${page.coerceAtLeast(1)}&limit=30")
        val items = json.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val obj = items.optJSONObject(i) ?: return@mapNotNull null
            val rawKey = obj.optString("key")
            val workPath = when {
                rawKey.startsWith("/works/OL") -> rawKey
                rawKey.matches(Regex("""OL\d+W""")) -> "/works/$rawKey"
                else -> return@mapNotNull null
            }
            Book(obj.optString("title"), obj.optJSONArray("author_name")?.optString(0).orEmpty(),
                "Open Library", "https://openlibrary.org$workPath",
                coverUrl = obj.optInt("cover_i").takeIf { it > 0 }?.let { "https://covers.openlibrary.org/b/id/$it-M.jpg" },
                publicationYear = obj.optInt("first_publish_year").takeIf { it in 1000..2100 },
                language = obj.optJSONArray("language")?.optString(0), format = "Online catalog")
        }
    }

    suspend fun searchInternetArchive(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val expression = "(${query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()}) AND mediatype:texts"
        val json = fetch("https://archive.org/advancedsearch.php?q=${encode(expression)}&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&rows=30&page=${page.coerceAtLeast(1)}&output=json")
        val items = json.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val obj = items.optJSONObject(i) ?: return@mapNotNull null
            val id = obj.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Book(obj.optString("title"), obj.optString("creator"), "Internet Archive",
                "https://archive.org/details/${encode(id)}",
                coverUrl = "https://archive.org/services/img/${encode(id)}",
                publicationYear = obj.optInt("year").takeIf { it in 1000..2100 },
                format = "Available files vary")
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
                "https://en.wikisource.org/wiki/${encode(title.replace(' ', '_')).replace("+", "%20")}",
                format = "Read online")
        }
    }

    /** Internet Archive's public video metadata index; open the official playback page. */
    suspend fun searchInternetArchiveVideos(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val safe = query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
        if (safe.isBlank()) return@withContext emptyList()
        val expression = "($safe) AND mediatype:movies"
        val json = fetch("https://archive.org/advancedsearch.php?q=${encode(expression)}&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&rows=25&page=${page.coerceAtLeast(1)}&output=json")
        val items = json.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val entry = items.optJSONObject(i) ?: return@mapNotNull null
            val id = entry.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Book(entry.optString("title").ifBlank { id }, entry.optString("creator"),
                "Internet Archive Video", "https://archive.org/details/${encode(id)}",
                coverUrl = "https://archive.org/services/img/${encode(id)}",
                publicationYear = entry.optInt("year").takeIf { it in 1000..2100 },
                format = "Public video listing")
        }
    }

    /** Google Books public metadata/official previews only; no paid-book download bypass. */
    suspend fun searchGoogleBooks(query: String): List<Book> = withContext(Dispatchers.IO) {
        val url = "https://www.googleapis.com/books/v1/volumes?q=${encode(query)}&maxResults=25"
        val items = fetch(url).optJSONArray("items") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val volume = items.optJSONObject(i) ?: return@mapNotNull null
            val info = volume.optJSONObject("volumeInfo") ?: return@mapNotNull null
            val title = info.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val id = volume.optString("id").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val date = info.optString("publishedDate")
            val year = date.take(4).toIntOrNull()?.takeIf { it in 1000..2100 }
            val preview = info.optString("previewLink").takeIf { it.startsWith("https://") }
                ?: "https://books.google.com/books?id=${encode(id)}"
            val thumbnail = info.optJSONObject("imageLinks")?.optString("thumbnail").orEmpty()
            Book(title, info.optJSONArray("authors")?.optString(0).orEmpty(), "Google Books", preview,
                coverUrl = thumbnail.takeIf { it.startsWith("https://") },
                publicationYear = year, language = info.optString("language"),
                format = "Official preview / listing")
        }
    }

    /** Free New Releases Atom feed only: the full Standard Ebooks OPDS is membership-gated. */
    suspend fun searchStandardEbooksRecent(query: String): List<Book> = withContext(Dispatchers.IO) {
        val xml = fetchText("https://standardebooks.org/feeds/atom/new-releases")
        NewsFeedParser.parse(xml).asSequence()
            .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) }
            .map { entry ->
                Book(entry.title, "", "Standard Ebooks — new releases", entry.link,
                    format = "Public-domain listing / EPUB")
            }.toList()
    }

    /** Rights-respecting discovery of public audiobook metadata (not a direct download parser). */
    suspend fun searchInternetArchiveAudio(query: String, page: Int = 1): List<Book> = withContext(Dispatchers.IO) {
        val safe = query.replace(Regex("[^\\p{L}\\p{N} ]"), " ").trim()
        if (safe.isBlank()) return@withContext emptyList()
        val expression = "($safe) AND mediatype:audio"
        val json = fetch("https://archive.org/advancedsearch.php?q=${encode(expression)}&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=year&rows=25&page=${page.coerceAtLeast(1)}&output=json")
        val items = json.optJSONObject("response")?.optJSONArray("docs") ?: return@withContext emptyList()
        (0 until items.length()).mapNotNull { i ->
            val entry = items.optJSONObject(i) ?: return@mapNotNull null
            val id = entry.optString("identifier").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Book(entry.optString("title").ifBlank { id }, entry.optString("creator"),
                "Internet Archive Audio", "https://archive.org/details/${encode(id)}",
                coverUrl = "https://archive.org/services/img/${encode(id)}",
                publicationYear = entry.optInt("year").takeIf { it in 1000..2100 },
                format = "Public audio listing")
        }
    }

    private fun fetchText(url: String): String {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Monogatari/1.0 (public book catalog)").build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Catalog feed HTTP ${response.code}")
            val body = response.body ?: error("Empty catalog feed")
            val bytes = body.byteStream().use { stream ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                while (true) {
                    val size = stream.read(buffer)
                    if (size <= 0) break
                    if (output.size() + size > 1_000_000) error("Catalog feed exceeds 1 MB")
                    output.write(buffer, 0, size)
                }
                output.toByteArray()
            }
            bytes.toString(Charsets.UTF_8)
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
