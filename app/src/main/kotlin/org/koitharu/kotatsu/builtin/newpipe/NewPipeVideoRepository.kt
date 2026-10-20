package org.koitharu.kotatsu.builtin.newpipe

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.media.MediaSubtitle
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.*
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.playlist.PlaylistInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import java.util.concurrent.ConcurrentHashMap

/** Video search and compatible combined/HLS playback. Channels and playlists are a later stage. */
class NewPipeVideoRepository(
    cache: MemoryContentCache,
    override val source: BuiltinSource = BuiltinSource.YOUTUBE,
    private val client: OkHttpClient,
) :
    CachingMangaRepository(cache), ContinuousMediaProvider {
    override val sortOrders = setOf(SortOrder.POPULARITY)
    override var defaultSortOrder = SortOrder.POPULARITY
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    private val service = when (source) {
        BuiltinSource.PEERTUBE -> ServiceList.PeerTube
        BuiltinSource.SOUNDCLOUD -> ServiceList.SoundCloud
        else -> ServiceList.YouTube
    }
    private val nextPage = ConcurrentHashMap<String, Page>()
    private val rssFeed = YouTubeChannelFeed(client)

    private val kioskNextPage = ConcurrentHashMap<String, Page>()

    /** Kiosk backing the current no-query list, or null when that list fell back to search. */
    @Volatile
    private var activeKiosk: String? = null

    /** Last kiosk that returned items, tried first next time. */
    @Volatile
    private var lastWorkingKiosk: String? = null

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> =
        withContext(Dispatchers.IO) {
            if (filter?.query.isNullOrBlank()) {
                val kioskItems = if (offset <= 0) {
                    loadKioskFirstPage()
                } else {
                    activeKiosk?.let(::loadKioskNextPage)
                }
                if (kioskItems != null) {
                    return@withContext kioskItems.mapNotNull(::toManga)
                }
            }
            val plan = NewPipeSearchPlan.create(source, filter?.query)
            val handler = service.searchQHFactory.fromQuery(plan.query, plan.filter, "")
            val (items, next) = if (offset <= 0) {
                val info = SearchInfo.getInfo(service, handler)
                info.relatedItems to info.nextPage
            } else {
                val page = nextPage[plan.cacheKey] ?: return@withContext emptyList()
                val response = SearchInfo.getMoreItems(service, handler, page)
                response.items to response.nextPage
            }
            if (next == null) nextPage.remove(plan.cacheKey) else nextPage[plan.cacheKey] = next
            items.mapNotNull(::toManga)
        }

    /**
     * First page of the popular list from the service kiosks. Returns null when no kiosk
     * produced items, so the caller falls back to the default search and the list is never empty.
     */
    private fun loadKioskFirstPage(): List<StreamInfoItem>? {
        activeKiosk = null
        val kiosks = try {
            service.kioskList
        } catch (_: Exception) {
            return null
        }
        val candidates = NewPipeKioskPlan.candidates(
            source = source,
            available = kiosks.availableKiosks,
            defaultId = kiosks.defaultKioskId,
            lastWorking = lastWorkingKiosk,
        )
        for (kioskId in candidates) {
            val page = try {
                val extractor = kiosks.getExtractorById(kioskId, null)
                extractor.fetchPage()
                extractor.initialPage
            } catch (_: Exception) {
                continue
            }
            val items = page.items.filterIsInstance<StreamInfoItem>()
            if (items.isEmpty()) continue
            val key = NewPipeKioskPlan.cacheKey(source, kioskId)
            val next = page.nextPage
            if (Page.isValid(next)) kioskNextPage[key] = next else kioskNextPage.remove(key)
            activeKiosk = kioskId
            lastWorkingKiosk = kioskId
            return items
        }
        return null
    }

    /** Continuation of the active kiosk; an empty list ends paging (chart kiosks have one page). */
    private fun loadKioskNextPage(kioskId: String): List<StreamInfoItem> {
        val key = NewPipeKioskPlan.cacheKey(source, kioskId)
        val page = kioskNextPage[key] ?: return emptyList()
        val response = try {
            service.kioskList.getExtractorById(kioskId, page).getPage(page)
        } catch (_: Exception) {
            kioskNextPage.remove(key)
            return emptyList()
        }
        val next = response.nextPage
        if (Page.isValid(next)) kioskNextPage[key] = next else kioskNextPage.remove(key)
        return response.items.filterIsInstance<StreamInfoItem>()
    }

    private fun toManga(item: InfoItem): Manga? {
        val entryKind = when (item) {
            is StreamInfoItem -> "video"
            is PlaylistInfoItem -> "playlist"
            is ChannelInfoItem -> "channel"
            else -> return null
        }
        return Manga(
            id = ArchiveMapping.uid(source, item.url),
            title = item.name,
            altTitles = emptySet(),
            url = item.url,
            publicUrl = item.url,
            rating = RATING_UNKNOWN,
            contentRating = ContentRating.SUGGESTIVE,
            coverUrl = (item.thumbnails.maxByOrNull { it.height } ?: item.thumbnails.lastOrNull())?.url, // largest, not the first (smallest) one
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

    override suspend fun getDetailsImpl(manga: Manga): Manga = withContext(Dispatchers.IO) {
        if (manga.tags.any { it.title == "channel" }) {
            val channel = ChannelInfo.getInfo(service, manga.url)
            val firstTab = channel.tabs.firstOrNull { it.url.contains("/videos", ignoreCase = true) }
                ?: channel.tabs.firstOrNull() ?: return@withContext manga.copy(chapters = emptyList())
            val tab = ChannelTabInfo.getInfo(service, firstTab)
            val videoItems = NewPipePageCollector.collect(
                first = tab.relatedItems.filterIsInstance<StreamInfoItem>(),
                next = tab.nextPage,
                key = { item: StreamInfoItem -> item.url },
                fetch = { page ->
                    val more = ChannelTabInfo.getMoreItems(service, firstTab, page)
                    more.items.filterIsInstance<StreamInfoItem>() to more.nextPage
                },
            )
            val published = if (source == BuiltinSource.YOUTUBE) {
                try {
                    rssFeed.fetch(channel.id.orEmpty())
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    emptyList()
                }
            } else emptyList()
            val merged = LinkedHashMap<String, Pair<String, Long>>()
            published.forEach { item -> merged[item.id] = item.url to item.publishedAt }
            videoItems.forEach { item ->
                val id = YouTubeChannelFeed.videoId(item.url) ?: item.url
                merged.putIfAbsent(id, item.url to 0L)
            }
            val chapters = merged.values.toList().asReversed().mapIndexed { index, (url, posted) ->
                    MangaChapter(
                        id = ArchiveMapping.uid(source, url),
                        title = published.firstOrNull { it.url == url }?.title ?: videoItems.firstOrNull { it.url == url }?.name.orEmpty(),
                        number = (index + 1).toFloat(),
                        volume = 0,
                        url = url,
                        scanlator = null,
                        uploadDate = posted,
                        branch = null,
                        source = source,
                    )
                }
            return@withContext manga.copy(chapters = chapters)
        }
        if (manga.url.contains("list=") || manga.url.contains("/playlist")) {
            val info = PlaylistInfo.getInfo(service, manga.url)
            val videoItems = NewPipePageCollector.collect(
                first = info.relatedItems.filterIsInstance<StreamInfoItem>(),
                next = info.nextPage,
                key = { item: StreamInfoItem -> item.url },
                fetch = { page ->
                    val more = PlaylistInfo.getMoreItems(service, manga.url, page)
                    more.items.filterIsInstance<StreamInfoItem>() to more.nextPage
                },
            )
            val chapters = videoItems.mapIndexed { index, item ->
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
        val info = StreamInfo.getInfo(service, manga.url)
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
        val info = StreamInfo.getInfo(service, chapter.url)
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
            if (source == BuiltinSource.SOUNDCLOUD) {
                info.audioStreams.mapNotNull { audio ->
                    audio.url?.takeIf(String::isNotBlank)?.let { MediaStream(it, "Audio") }
                }.also(::addAll)
            }
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
