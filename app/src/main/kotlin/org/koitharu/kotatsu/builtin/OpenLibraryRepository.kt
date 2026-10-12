package org.koitharu.kotatsu.builtin

import kotlinx.coroutines.Dispatchers
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
) : CachingMangaRepository(cache) {
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
        return OpenLibraryMapping.map(fetch(url), readableNowOnly = true)
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val key = manga.url.takeIf { it.startsWith("/works/") } ?: return manga
        val work = fetch("https://openlibrary.org$key.json")
        val description = when (val value = work.opt("description")) {
            is String -> value
            is JSONObject -> value.optString("value")
            else -> manga.description
        }
        return manga.copy(description = description)
    }
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
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
