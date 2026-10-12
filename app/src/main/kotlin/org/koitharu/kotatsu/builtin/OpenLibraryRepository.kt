package org.koitharu.kotatsu.builtin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder

/** Native Open Library search with default public-readability filtering. */
class OpenLibraryRepository(
    cache: MemoryContentCache,
    private val client: OkHttpClient,
    context: Context,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    private val archive = ArchiveRepository(BuiltinSource.IA_BOOKS, cache, client, context)
    override val source = BuiltinSource.OPEN_LIBRARY
    override val sortOrders = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)
    override var defaultSortOrder = SortOrder.POPULARITY
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val query = filter?.query?.trim().orEmpty()
        val page = offset.coerceAtLeast(0) / 30 + 1
        val url = if (query.isEmpty()) {
            "https://openlibrary.org/trending/daily.json?limit=30&page=$page"
        } else {
            "https://openlibrary.org/search.json?q=${ArchiveMapping.encode(query)}" +
                "&fields=key,title,author_name,first_publish_year,cover_i,ebook_access,ia,subject,ratings_average,number_of_pages_median" +
                "&limit=30&page=$page"
        }
        return OpenLibraryMapping.map(fetch(url), readableNowOnly = query.isNotEmpty())
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val key = manga.url.takeIf { it.startsWith("/works/") } ?: return manga
        val work = fetch("https://openlibrary.org$key.json")
        val description = when (val value = work.opt("description")) {
            is String -> value
            is JSONObject -> value.optString("value")
            else -> manga.description
        }
        val result = fetch(
            "https://openlibrary.org/search.json?q=${ArchiveMapping.encode(manga.title)}" +
                "&fields=key,ebook_access,ia&limit=20",
        )
        val docs = result.optJSONArray("docs")
        val match = (0 until (docs?.length() ?: 0)).asSequence()
            .mapNotNull { docs?.optJSONObject(it) }
            .firstOrNull { it.optString("key") == key && it.optString("ebook_access") == "public" }
        val ia = match?.optJSONArray("ia")?.optString(0)?.takeIf { it.isNotBlank() }
        if (ia != null) {
            val archiveManga = manga.copy(url = "/details/$ia", source = BuiltinSource.IA_BOOKS)
            val archiveDetails = archive.getDetails(archiveManga)
            val chapters = archiveDetails.chapters?.map { it.copy(source = source) }
            if (!chapters.isNullOrEmpty()) return manga.copy(description = description, chapters = chapters)
        }
        return manga.copy(description = description)
    }
    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String =
        archive.getChapterHtml(title, chapter)

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()

    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = listOf(
        MangaPage(ArchiveMapping.uid(source, chapter.url), chapter.url, null, source),
    )
    override suspend fun getPageUrl(page: MangaPage): String = page.url

    private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Monogatari/0.9 (+https://github.com/coolchan03/Monogatari)")
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) error("Open Library HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty Open Library response"))
        }
    }
}
