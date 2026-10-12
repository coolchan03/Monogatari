package org.koitharu.kotatsu.builtin.ao3

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
import org.koitharu.kotatsu.core.prefs.AppSettings
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

/** AO3 native source. HTML adapters are isolated in Ao3Parser for fixture tests. */
class Ao3Repository(
    cache: MemoryContentCache,
    private val client: OkHttpClient,
    private val settings: AppSettings,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    override val source = BuiltinSource.AO3
    override val sortOrders = setOf(SortOrder.POPULARITY, SortOrder.UPDATED, SortOrder.NEWEST)
    override var defaultSortOrder = SortOrder.UPDATED
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val query = filter?.query?.trim().orEmpty()
        val column = when (order) {
            SortOrder.POPULARITY -> "kudos_count"
            SortOrder.NEWEST -> "created_at"
            else -> "revised_at"
        }
        val ratings = when (settings.contentLevel) {
            "SAFE" -> listOf(10)
            "SUGGESTIVE" -> listOf(10, 11)
            else -> listOf(9, 10, 11, 12, 13)
        }
        val queryParts = buildList {
            add("page=${offset.coerceAtLeast(0) / 20 + 1}")
            add("work_search[query]=${ArchiveMapping.encode(query)}")
            add("work_search[language_id]=en")
            add("work_search[sort_column]=$column")
            add("work_search[sort_direction]=desc")
            add("work_search[category_ids][]=23")
            ratings.forEach { add("work_search[rating_ids][]=$it") }
        }
        val html = fetch("/works/search?${queryParts.joinToString("&")}")
        return Ao3Parser.parseSearch(html).works.map { item ->
            val path = "/works/${item.id}"
            val rating = when {
                item.rating?.contains("Explicit", true) == true ||
                item.rating?.contains("Mature", true) == true -> ContentRating.ADULT
                item.rating?.contains("General", true) == true -> ContentRating.SAFE
                else -> ContentRating.SUGGESTIVE
            }
            Manga(
                id = ArchiveMapping.uid(source, path),
                title = item.title,
                altTitles = emptySet(),
                url = path,
                publicUrl = "https://archiveofourown.org$path",
                rating = RATING_UNKNOWN,
                contentRating = rating,
                coverUrl = null,
                tags = (item.tags + item.fandoms + listOfNotNull(item.category) +
                    item.warnings.map { "Warning: $it" })
                    .map { MangaTag(it, it, source) }.toSet(),
                state = null,
                authors = item.authors.toSet(),
                largeCoverUrl = null,
                description = item.summaryHtml,
                chapters = null,
                source = source,
            )
        }
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val path = manga.url.substringBefore('?')
        val adult = if (settings.contentLevel == "EVERYTHING") "?view_adult=true" else ""
        val html = fetch("$path/navigate$adult")
        val chapters = Ao3Parser.parseNavigate(html)
        val entries = if (chapters.isEmpty()) {
            listOf(Ao3Parser.Chapter(manga.title, path, null))
        } else chapters
        return manga.copy(chapters = entries.mapIndexed { index, c ->
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
        })
    }

    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> =
        listOf(MangaPage(ArchiveMapping.uid(source, chapter.url), chapter.url, null, source))

    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPageUrl(page: MangaPage): String = page.url

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String {
        val path = chapter.url
        val adult = if (settings.contentLevel == "EVERYTHING") "?view_adult=true" else ""
        val html = fetch(path + adult)
        return Ao3Parser.parseChapterText(html).ifBlank { error("AO3 chapter text not found") }
    }
    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()

    private suspend fun fetch(path: String): String = withContext(Dispatchers.IO) {
        val url = "https://archiveofourown.org$path"
        client.newCall(
            Request.Builder().url(url)
                .header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.9)")
                .build(),
        ).execute().use { response ->
            val html = response.body?.string().orEmpty()
            if (Ao3Parser.isLoginRequired(html, response.request.url.toString())) {
                error("Log in to AO3 to read this work")
            }
            if (!response.isSuccessful) error("AO3 HTTP ${response.code}")
            html
        }
    }
}
