package org.koitharu.kotatsu.builtin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.BookMetadataProvider
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.media.epub.EpubBook
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.File
import java.net.URLEncoder

/** Built-in Gutendex book source with cached EPUB reading. */
class GutenbergRepository(
    cache: MemoryContentCache,
    private val client: OkHttpClient,
    private val context: Context,
    private val bookMetadata: BookMetadataProvider,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    override val source = BuiltinSource.GUTENBERG
    override val sortOrders = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)
    override var defaultSortOrder = SortOrder.POPULARITY
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val parsed = GutenbergGenres.parse(filter?.query?.trim().orEmpty())
        val query = parsed.text
        val page = offset.coerceAtLeast(0) / 32 + 1
        val url = buildString {
            append("https://gutendex.com/books/?languages=en&page=").append(page)
            parsed.topic?.let { append("&topic=").append(URLEncoder.encode(it, "UTF-8")) }
            if (query.isNotEmpty()) {
                append("&search=").append(URLEncoder.encode(query, "UTF-8"))
            } else if (order == SortOrder.UPDATED) {
                append("&sort=recent")
            } else {
                append("&sort=popular")
            }
        }
        return GutenbergMapping.mapBooks(fetch(url))
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val id = bookId(manga.url)
        val metadata = fetch("https://gutendex.com/books/$id")
        val epub = epubFile(id, metadata)
        val book = EpubBook.open(epub)
        val chapters = book.chapters.mapIndexed { index, chapter ->
            val chapterUrl = "$id#${chapter.href}"
            MangaChapter(
                id = uid(chapterUrl),
                title = chapter.title,
                number = (index + 1).toFloat(),
                volume = 0,
                url = chapterUrl,
                scanlator = null,
                uploadDate = 0L,
                branch = null,
                source = source,
            )
        }
        return bookMetadata.enrich(manga.copy(chapters = chapters, description = manga.description ?: book.author))
    }

    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = listOf(
        MangaPage(id = uid(chapter.url), url = chapter.url, preview = null, source = source),
    )

    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPageUrl(page: MangaPage): String = page.url

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String {
        val id = bookId(chapter.url)
        val file = File(context.cacheDir, "books/gutenberg/$id.epub")
        val metadata = if (!file.isFile) fetch("https://gutendex.com/books/$id") else null
        val book = EpubBook.open(epubFile(id, metadata))
        return book.chapterHtml(chapter.url.substringAfter('#'))
    }

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()

    private fun bookId(url: String): Int =
        url.substringBefore('#').substringAfterLast('/').toIntOrNull()?.takeIf { it > 0 }
            ?: error("Invalid Gutenberg book id")

    private suspend fun epubFile(id: Int, metadata: JSONObject?): File = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "books/gutenberg/$id.epub")
        if (target.isFile && target.length() > 0L) return@withContext target
        val formats = (metadata ?: fetch("https://gutendex.com/books/$id")).optJSONObject("formats")
        val url = formats?.optString("application/epub+zip")
            ?.takeIf { it.startsWith("https://") } ?: error("No EPUB download for Gutenberg $id")
        target.parentFile?.mkdirs()
        val temp = File(target.parentFile, "$id.epub.tmp")
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.9)").build()).execute().use { response ->
                if (!response.isSuccessful) error("EPUB download HTTP ${response.code}")
                val body = response.body ?: error("Empty EPUB download")
                body.byteStream().use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var count = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            if (count > 128L * 1024 * 1024) error("EPUB too large")
                            output.write(buffer, 0, n)
                        }
                    }
                }
            }
            if (!temp.renameTo(target)) error("Could not cache EPUB")
            target
        } finally {
            temp.delete()
        }
    }

    private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.9)").build()).execute().use { response ->
            if (!response.isSuccessful) error("Gutendex HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty Gutendex response"))
        }
    }

    private fun uid(url: String): Long {
        var hash = 1125899906842597L
        source.name.forEach { hash = 31 * hash + it.code }
        url.forEach { hash = 31 * hash + it.code }
        return hash
    }
}
