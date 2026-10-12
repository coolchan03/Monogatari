package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.koitharu.kotatsu.parsers.model.Manga
import java.net.URLEncoder
import java.security.MessageDigest
import javax.inject.Inject

/** Enrich missing metadata without replacing user-supplied title/author fields. */
class BookMetadataProvider @Inject constructor(
    db: MangaDatabase,
    @MangaHttpClient private val client: OkHttpClient,
) {
    private val dao = db.getBookMetadataDao()

    suspend fun enrich(book: Manga): Manga {
        if (book.coverUrl != null && !book.description.isNullOrBlank()) return book
        val metadata = runCatching {
            lookup(book.title, book.authors.firstOrNull().orEmpty())
        }.getOrNull() ?: return book
        return book.copy(
            coverUrl = book.coverUrl ?: metadata.coverUrl,
            description = book.description?.takeIf(String::isNotBlank) ?: metadata.description,
        )
    }

    suspend fun lookup(title: String, author: String): BookMetadataEntity? {
        if (title.isBlank()) return null
        val key = digest(title.trim().lowercase() + "|" + author.trim().lowercase())
        val cached = dao.find(key)
        if (cached != null && System.currentTimeMillis() - cached.cachedAt < 30L * 86400000L) {
            return cached
        }
        val query = "https://openlibrary.org/search.json?title=${encode(title)}&author=${encode(author)}" +
            "&limit=1&fields=cover_i,first_sentence,subject,number_of_pages_median"
        return withContext(Dispatchers.IO) {
            client.newCall(Request.Builder().url(query)
                .header("User-Agent", "Monogatari/0.9 (+https://github.com/coolchan03/Monogatari)")
                .build()).execute().use { response ->
                if (!response.isSuccessful) return@use cached
                val entry = JSONObject(response.body?.string().orEmpty()).optJSONArray("docs")
                    ?.optJSONObject(0) ?: return@use cached
                val cover = entry.optInt("cover_i").takeIf { it > 0 }
                    ?.let { "https://covers.openlibrary.org/b/id/$it-L.jpg" }
                val description = when (val first = entry.opt("first_sentence")) {
                    is String -> first
                    else -> null
                }
                val pages = entry.optInt("number_of_pages_median").takeIf { it > 0 }
                BookMetadataEntity(key, cover, description, pages, System.currentTimeMillis())
                    .also { dao.upsert(it) }
            }
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
}
