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
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
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
            val raw = filter?.query?.trim().orEmpty().ifEmpty { "BL drama" }
            val kind = when {
                raw.startsWith("playlist:", true) -> "playlists"
                else -> "videos"
            }
            val query = if (kind == "videos") raw else raw.substringAfter(':').trim()
            val service = ServiceList.YouTube
            val handler = service.searchQHFactory.fromQuery(query, listOf(kind), "")
            val (items, next) = if (offset <= 0) {
                val info = SearchInfo.getInfo(service, handler)
                info.relatedItems to info.nextPage
            } else {
                val page = nextPage[raw] ?: return@withContext emptyList()
                val response = SearchInfo.getMoreItems(service, handler, page)
                response.items to response.nextPage
            }
            if (next == null) nextPage.remove(raw) else nextPage[raw] = next
            items.mapNotNull { item ->
                val entryKind = when (item) {
                    is StreamInfoItem -> "video"
                    is PlaylistInfoItem -> "playlist"
                    else -> return@mapNotNull null
                }
                Manga(
                    id = ArchiveMapping.uid(source, item.url),
                    title = item.name,
                    altTitles = emptySet(),
                    url = item.url,
                    publicUrl = item.url,
                    rating = RATING_UNKNOWN,
                    contentRating = ContentRating.SUGGESTIVE,
                    coverUrl = item.thumbnails.firstOrNull()?.url,
                    tags = setOf(MangaTag(entryKind, entryKind, source)),
                    state = null,
                    authors = (item as? StreamInfoItem)?.uploaderName
                        ?.takeIf(String::isNotBlank)?.let(::setOf) ?: emptySet(),
                    largeCoverUrl = null,
                    description = (item as? StreamInfoItem)?.shortDescription.orEmpty(),
                    chapters = null,
                    source = source,
                )
            }
        }

    override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
        if (manga.url.contains("list=") || manga.url.contains("/playlist")) {
            val info = PlaylistInfo.getInfo(ServiceList.YouTube, manga.url)
            val chapters = info.relatedItems.filterIsInstance<StreamInfoItem>()
                .mapIndexed { index, item ->
                    MangaChapter(
                        id = ArchiveMapping.uid(source, item.url),
                        title = item.name,
                        number = (index + 1).toFloat(),
                        volume = 0,
                        url = item.url,
                        scanlator = null,
                        uploadDate = 0L,
                        branch = null,
                        source = source,
                    )
                }
            return@withContext manga.copy(chapters = chapters)
        }
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
        val subtitles = info.subtitles.mapNotNull { track ->
            track.url?.takeIf(String::isNotBlank)?.let { MediaSubtitle(it, track.languageTag) }
        }
        val hls = info.hlsUrl?.takeIf(String::isNotBlank)
        val dash = info.dashMpdUrl?.takeIf(String::isNotBlank)
        val muxed = info.videoStreams.mapNotNull { stream ->
            val url = stream.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
            MediaStream(url, stream.resolution.orEmpty(), subtitles = subtitles)
        }
        val audioUrl = info.audioStreams
            .filter { it.url?.isNotBlank() == true }
            .maxByOrNull { it.averageBitrate }?.url
        val separated = if (audioUrl.isNullOrBlank()) emptyList() else info.videoOnlyStreams
            .mapNotNull { stream ->
                val url = stream.url?.takeIf(String::isNotBlank) ?: return@mapNotNull null
                MediaStream(url, stream.resolution.orEmpty(), subtitles = subtitles, audioUrl = audioUrl)
            }
        buildList {
            if (hls != null) add(MediaStream(hls, "HLS", subtitles = subtitles, mimeType = "application/x-mpegURL"))
            if (dash != null) add(MediaStream(dash, "DASH", subtitles = subtitles, mimeType = "application/dash+xml"))
            addAll(muxed)
            addAll(separated)
        }
    }

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String = ""
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
    override suspend fun getPageUrl(page: MangaPage): String = page.url
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
}
