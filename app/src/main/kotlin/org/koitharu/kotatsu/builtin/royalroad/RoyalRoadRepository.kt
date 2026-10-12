package org.koitharu.kotatsu.builtin.royalroad

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
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

/** Royal Road listings and chapter pages, using the shared HTML reader. */
class RoyalRoadRepository(
    cache: MemoryContentCache,
    private val client: OkHttpClient,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    override val source = BuiltinSource.ROYAL_ROAD
    override val sortOrders = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)
    override var defaultSortOrder = SortOrder.UPDATED
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val query = filter?.query?.trim().orEmpty()
        val page = offset.coerceAtLeast(0) / 20 + 1
        val url = if (query.isNotBlank()) "/fictions/search?title=${ArchiveMapping.encode(query)}&page=$page"
            else if (order == SortOrder.POPULARITY) "/fictions/best-rated?page=$page"
            else "/fictions/latest-updates?page=$page"
        return RoyalRoadParser.search(fetch(url)).map { item ->
            Manga(
                id = ArchiveMapping.uid(source, item.url),
                title = item.title,
                altTitles = emptySet(),
                url = item.url,
                publicUrl = "https://www.royalroad.com${item.url}",
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.SUGGESTIVE,
                coverUrl = item.cover,
                tags = item.tags.map { MangaTag(it, it, source) }.toSet(),
                state = null,
                authors = emptySet(),
                largeCoverUrl = null,
                description = null,
                chapters = null,
                source = source,
            )
        }
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val html = fetch(manga.url)
        val doc = Jsoup.parse(html, "https://www.royalroad.com")
        val description = doc.selectFirst(".description")?.html()
        val chapters = RoyalRoadParser.chapters(html).mapIndexed { index, chapter ->
            MangaChapter(
                id = ArchiveMapping.uid(source, chapter.url),
                title = chapter.title,
                number = (index + 1).toFloat(),
                volume = 0,
                url = chapter.url,
                scanlator = null,
                uploadDate = chapter.posted?.times(1000L) ?: 0L,
                branch = null,
                source = source,
            )
        }
        return manga.copy(description = description, chapters = chapters)
    }

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String =
        RoyalRoadParser.chapterContent(fetch(chapter.url)).ifEmpty { error("Royal Road chapter is unavailable") }

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> =
        listOf(MangaPage(ArchiveMapping.uid(source, chapter.url), chapter.url, null, source))
    override suspend fun getPageUrl(page: MangaPage): String = page.url
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

    private suspend fun fetch(path: String): String = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url("https://www.royalroad.com$path")
            .header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.9)").build())
            .execute().use { response ->
                if (!response.isSuccessful) error("Royal Road HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
    }
}
