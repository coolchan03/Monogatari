package org.koitharu.kotatsu.builtin.scribblehub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
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
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder

/** Scribble Hub series, chapters and direct novel HTML reader. */
class ScribbleHubRepository(
    cache: MemoryContentCache,
    private val client: OkHttpClient,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    override val source = BuiltinSource.SCRIBBLE_HUB
    override val sortOrders = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)
    override var defaultSortOrder = SortOrder.UPDATED
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val query = filter?.query?.trim().orEmpty()
        val page = offset.coerceAtLeast(0) / 20 + 1
        val path = if (query.isBlank()) "/genre/yaoi/?pg=$page"
            else "/?s=${ArchiveMapping.encode(query)}&post_type=fictionposts&paged=$page"
        return ScribbleHubParser.search(fetch(path)).map { item ->
            Manga(
                id = ArchiveMapping.uid(source, item.url),
                title = item.title,
                altTitles = emptySet(),
                url = item.url,
                publicUrl = item.url,
                rating = RATING_UNKNOWN,
                contentRating = ContentRating.SUGGESTIVE,
                coverUrl = item.cover,
                tags = emptySet(),
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
        val html = fetch(manga.url.trimEnd('/') + "/?toc=1")
        val chapters = ScribbleHubParser.chapters(html).mapIndexed { index, c ->
            MangaChapter(
                id = ArchiveMapping.uid(source, c.url),
                title = c.title,
                number = (index + 1).toFloat(),
                volume = 0,
                url = c.url,
                scanlator = null,
                uploadDate = 0L,
                branch = null,
                source = source,
            )
        }
        return manga.copy(chapters = chapters)
    }

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String =
        ScribbleHubParser.chapterContent(fetch(chapter.url)).ifEmpty { error("Scribble Hub chapter text missing") }
    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> =
        listOf(MangaPage(ArchiveMapping.uid(source, chapter.url), chapter.url, null, source))
    override suspend fun getPageUrl(page: MangaPage): String = page.url
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

    private suspend fun fetch(pathOrUrl: String): String = withContext(Dispatchers.IO) {
        val url = if (pathOrUrl.startsWith("https://www.scribblehub.com/")) pathOrUrl
            else "https://www.scribblehub.com$pathOrUrl"
        client.newCall(Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.9)").build())
            .execute().use { response ->
                if (!response.isSuccessful) error("Scribble Hub HTTP ${response.code}")
                response.body?.string().orEmpty()
            }
    }
}
