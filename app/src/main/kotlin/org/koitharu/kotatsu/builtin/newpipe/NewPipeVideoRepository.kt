package org.koitharu.kotatsu.builtin.newpipe

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.media.MediaSubtitle
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.*
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import java.util.concurrent.ConcurrentHashMap

/** Video search and compatible combined/HLS playback. Channels and playlists are a later stage. */
class NewPipeVideoRepository(cache: MemoryContentCache) :
    CachingMangaRepository(cache), ContinuousMediaProvider {
    override val source = BuiltinSource.YOUTUBE
    override val sortOrders = setOf(SortOrder.POPULARITY)
    override var defaultSortOrder = SortOrder.POPULARITY
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    private val nextPage = ConcurrentHashMap<String, Page>()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> =
        withContext(Dispatchers.IO) {
            val query = filter?.query?.trim().orEmpty().ifEmpty { "BL drama" }
            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(query, listOf("videos"), "")
            val (items, next) = if (offset <= 0) {
                val info = SearchInfo.getInfo(service, handler)
                info.relatedItems to info.nextPage
            } else {
                val page = nextPage[query] ?: return@withContext emptyList()
                val response = SearchInfo.getMoreItems(service, handler, page)
                response.items to response.nextPage
            }
            if (next == null) nextPage.remove(query) else nextPage[query] = next
            items.filterIsInstance<StreamInfoItem>().map { item ->
                Manga(
                    id = ArchiveMapping.uid(source, item.url),
                    title = item.name,
                    altTitles = emptySet(),
                    url = item.url,
                    publicUrl = item.url,
                    rating = RATING_UNKNOWN,
                    contentRating = ContentRating.SUGGESTIVE,
                    coverUrl = item.thumbnails.firstOrNull()?.url,
                    tags = emptySet(),
                    state = null,
                    authors = item.uploaderName?.takeIf(String::isNotBlank)?.let(::setOf) ?: emptySet(),
                    largeCoverUrl = null,
                    description = item.shortDescription,
                    chapters = null,
                    source = source,
                )
            }
        }

    override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, manga.url)
        val chapter = MangaChapter(
            id = ArchiveMapping.uid(source, manga.url),
            title = manga.title,
            number = 1f,
            volume = 0,
            url = manga.url,
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = source,
        )
        manga.copy(
            chapters = listOf(chapter),
            contentRating = if (info.ageLimit >= 18) ContentRating.ADULT else manga.contentRating,
            description = info.description?.content ?: manga.description,
        )
    }

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = withContext(Dispatchers.IO) {
        val info = StreamInfo.getInfo(ServiceList.YouTube, chapter.url)
        val subtitles = info.subtitles.map { MediaSubtitle(it.url, it.languageTag) }
        val hls = info.hlsUrl?.takeIf(String::isNotBlank)
        val streams = info.videoStreams.filter { it.url.isNotBlank() }.map {
            MediaStream(it.url, it.resolution, subtitles = subtitles)
        }
        if (hls != null) listOf(MediaStream(hls, "HLS", subtitles = subtitles)) + streams else streams
    }

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String = ""
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
    override suspend fun getPageUrl(page: MangaPage): String = page.url
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
}
