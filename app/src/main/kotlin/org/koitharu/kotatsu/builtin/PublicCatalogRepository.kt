package org.koitharu.kotatsu.builtin

import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.media.PublicBookCatalog
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.net.URI

/** Safe official catalog entries for unified Books search. */
class PublicCatalogRepository(
    override val source: BuiltinSource,
    cache: MemoryContentCache,
    private val catalog: PublicBookCatalog,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    override val sortOrders = setOf(SortOrder.RELEVANCE)
    override var defaultSortOrder = SortOrder.RELEVANCE
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(
        offset: Int, order: SortOrder?, filter: MangaListFilter?,
    ): List<Manga> {
        if (offset > 0) return emptyList()
        val query = filter?.query?.trim().orEmpty().take(120)
        val items = when (source) {
            BuiltinSource.FADED_PAGE -> catalog.searchFadedPage(query)
            BuiltinSource.GUTENBERG_AU -> catalog.searchGutenbergAustralia(query)
            BuiltinSource.GLOBAL_GREY -> catalog.searchGlobalGrey(query)
            BuiltinSource.BAEN_FREE -> catalog.searchBaenFreeLibrary(query)
            else -> emptyList()
        }
        return items.asSequence()
            .filter { approvedUrl(it.url) }
            .distinctBy { it.url }
            .take(120).map { it.toManga() }.toList()
    }

    private fun approvedUrl(url: String): Boolean {
        val host = when (source) {
            BuiltinSource.FADED_PAGE -> "www.fadedpage.com"
            BuiltinSource.GUTENBERG_AU -> "www.gutenberg.net.au"
            BuiltinSource.GLOBAL_GREY -> "www.globalgreyebooks.com"
            BuiltinSource.BAEN_FREE -> "www.baen.com"
            else -> return false
        }
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(host, ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.userInfo == null && uri.fragment == null
    }

    private fun PublicBookCatalog.Book.toManga() = Manga(
        id = ArchiveMapping.uid(source, url),
        title = title, altTitles = emptySet(),
        url = url, publicUrl = url,
        rating = RATING_UNKNOWN, contentRating = null,
        coverUrl = coverUrl, tags = emptySet(),
        state = null,
        authors = author.takeIf(String::isNotBlank)?.let { setOf(it) } ?: emptySet(),
        largeCoverUrl = null,
        description = listOfNotNull(
            format?.takeIf(String::isNotBlank),
            publicationYear?.let { "Publication year: $it" },
            language?.takeIf(String::isNotBlank),
        ).joinToString(" | ").takeIf(String::isNotBlank),
        chapters = null, source = source,
    )

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        require(approvedUrl(manga.url)) { "Untrusted public-catalog link" }
        val chapter = MangaChapter(
            id = ArchiveMapping.uid(source, manga.url + "#external"),
            title = "Read on ${source.title}",
            number = 1f, volume = 0,
            url = manga.url, scanlator = null,
            uploadDate = 0L, branch = null, source = source,
        )
        return manga.copy(chapters = listOf(chapter))
    }

    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
        require(approvedUrl(chapter.url)) { "Untrusted public-catalog page link" }
        return listOf(MangaPage(
            id = ArchiveMapping.uid(source, chapter.url + "#page"),
            url = chapter.url, preview = null, source = source,
        ))
    }

    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

    override suspend fun getPageUrl(page: MangaPage): String =
        page.url.takeIf(::approvedUrl) ?: error("Untrusted public-catalog page URL")

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String {
        val link = chapter.url.takeIf(::approvedUrl)
            ?: error("Untrusted public-catalog link")
        val safeUrl = escapeHtml(link)
        val safeName = escapeHtml(source.title)
        return """
            <html><head><meta name="viewport" content="width=device-width,initial-scale=1" /></head>
            <body style="font-family:sans-serif;padding:1.25rem;line-height:1.6">
            <h2>$safeName</h2>
            <p>This provider offers an official reading page.
            Copyright and availability vary by region.</p>
            <p><a href="$safeUrl">Open the official book page</a></p>
            </body></html>
        """.trimIndent()
    }

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()
    private fun escapeHtml(text: String): String = text.replace("&", "&amp;")
        .replace(34.toChar().toString(), "&quot;")
        .replace("<", "&lt;").replace(">", "&gt;")
}