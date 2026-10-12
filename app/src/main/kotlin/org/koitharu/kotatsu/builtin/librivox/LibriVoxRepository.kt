package org.koitharu.kotatsu.builtin.librivox

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.*

class LibriVoxRepository(cache: MemoryContentCache, private val client: OkHttpClient) :
    CachingMangaRepository(cache), ContinuousMediaProvider {
    override val source = BuiltinSource.LIBRIVOX
    override val sortOrders = setOf(SortOrder.UPDATED)
    override var defaultSortOrder = SortOrder.UPDATED
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val term = filter?.query?.trim().orEmpty()
        val url = "https://librivox.org/api/feed/audiobooks/?format=json&extended=1&limit=30&offset=$offset" +
            if (term.isEmpty()) "" else "&title=${ArchiveMapping.encode("^$term")}"
        val books = fetch(url).optJSONArray("books") ?: return emptyList()
        return (0 until books.length()).mapNotNull { i ->
            val book = books.optJSONObject(i) ?: return@mapNotNull null
            val id = book.optString("id").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val title = book.optString("title").takeIf(String::isNotBlank) ?: return@mapNotNull null
            val archiveId = book.optString("url_iarchive").trimEnd('/').substringAfterLast('/')
            Manga(
                id = ArchiveMapping.uid(source, "/audiobook/$id"),
                title = title, altTitles = emptySet(), url = "/audiobook/$id",
                publicUrl = book.optString("url_librivox").takeIf { it.startsWith("https://") },
                rating = RATING_UNKNOWN, contentRating = ContentRating.SAFE,
                coverUrl = archiveId.takeIf(String::isNotBlank)?.let { "https://archive.org/services/img/$it" },
                tags = emptySet(), state = null, authors = emptySet(), largeCoverUrl = null,
                description = book.optString("description").orEmpty(), chapters = null, source = source,
            )
        }
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val id = manga.url.substringAfterLast('/')
        val sections = fetch("https://librivox.org/api/feed/audiobooks/?id=$id&format=json&extended=1")
            .optJSONArray("books")?.optJSONObject(0)?.optJSONArray("sections") ?: return manga
        return manga.copy(chapters = (0 until sections.length()).mapNotNull { i ->
            val item = sections.optJSONObject(i) ?: return@mapNotNull null
            val url = item.optString("listen_url").takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            MangaChapter(id = ArchiveMapping.uid(source, url), title = item.optString("title").ifBlank { "Chapter ${i + 1}" },
                number = (i + 1).toFloat(), volume = 0, url = url, scanlator = null,
                uploadDate = 0L, branch = null, source = source)
        })
    }

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> =
        chapter.url.takeIf { it.startsWith("https://") }?.let { listOf(MediaStream(it, "Audio")) }.orEmpty()
    override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPageUrl(page: MangaPage) = page.url

    private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) error("LibriVox HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty LibriVox response"))
        }
    }
}
