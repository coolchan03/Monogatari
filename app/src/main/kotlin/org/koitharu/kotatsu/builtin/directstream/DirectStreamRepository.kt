package org.koitharu.kotatsu.builtin.directstream

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
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.IOException

/**
 * Plays links the user pastes into search: HLS (.m3u8), DASH (.mpd), plain audio/video files and M3U playlists
 * (each playlist entry becomes a title). Only links the user supplies are opened; nothing is discovered or
 * bypassed, and DRM-protected streams simply fail to play. Quality labels show resolution, codecs and bitrate
 * so a stream that won't play on this device can be understood.
 */
class DirectStreamRepository(cache: MemoryContentCache, private val client: OkHttpClient) :
	CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.DIRECT_STREAMS
	override val sortOrders = setOf(SortOrder.RELEVANCE)
	override var defaultSortOrder = SortOrder.RELEVANCE
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val link = filter?.query?.trim().orEmpty()
		if (link.isEmpty()) return emptyList()
		// Ordinary searches (a title, not a link) simply have no results here, so global search stays quiet.
		if (!StreamLinks.isHttpUrl(link) || offset > 0) return emptyList()
		val kind = StreamLinks.kindFromUrl(link)
		if (kind == StreamLinks.Kind.DASH || kind == StreamLinks.Kind.FILE) return listOf(entryManga(StreamLinks.Entry(titleOf(link), link)))
		val text = fetchText(link)
		val entries = if (StreamLinks.isEntryList(text)) StreamLinks.parseEntries(text, base = link) else emptyList()
		return if (entries.isEmpty()) listOf(entryManga(StreamLinks.Entry(titleOf(link), link))) else entries.map(::entryManga)
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga = manga.copy(
		chapters = listOf(
			MangaChapter(
				id = ArchiveMapping.uid(source, "/episode/${manga.url}"),
				title = manga.title,
				number = 1f,
				volume = 0,
				url = manga.url,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			),
		),
	)

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		val url = chapter.url
		require(StreamLinks.isHttpUrl(url)) { "Not a web link" }
		return when (StreamLinks.kindFromUrl(url)) {
			StreamLinks.Kind.HLS -> hlsStreams(url)
			StreamLinks.Kind.DASH -> {
				val labels = runCatching { StreamLinks.parseDashLabels(fetchText(url)) }.getOrDefault(emptyList())
				listOf(MediaStream(url, "DASH · " + labels.take(3).joinToString(", ").ifEmpty { "adaptive" }, mimeType = "application/dash+xml"))
			}
			else -> listOf(MediaStream(url, "Direct file", mimeType = StreamLinks.mimeFor(url)))
		}
	}

	private suspend fun hlsStreams(url: String): List<MediaStream> {
		val text = fetchText(url)
		if (!StreamLinks.isMasterPlaylist(text)) {
			return listOf(MediaStream(url, "HLS", mimeType = "application/x-mpegURL"))
		}
		val variants = StreamLinks.parseHlsVariants(text, url)
		return listOf(MediaStream(url, "Auto (adaptive)", mimeType = "application/x-mpegURL")) +
			variants.map { MediaStream(it.url, it.label, mimeType = "application/x-mpegURL") }
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun entryManga(entry: StreamLinks.Entry) = Manga(
		id = ArchiveMapping.uid(source, "/link/${entry.url}"),
		title = entry.title,
		altTitles = emptySet(),
		url = entry.url,
		publicUrl = entry.url,
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = entry.logo,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		largeCoverUrl = null,
		description = entry.url,
		chapters = null,
		source = source,
	)

	private fun titleOf(url: String) = url.substringBefore('#').substringBefore('?').substringAfterLast('/').ifBlank { url }

	private suspend fun fetchText(url: String): String = withContext(Dispatchers.IO) {
		client.newCall(Request.Builder().url(url).build()).execute().use { response ->
			if (!response.isSuccessful) throw IOException("The link answered with HTTP ${response.code}")
			// Playlists are small; refuse to read a huge file as text by mistake.
			response.body?.source()?.let { src ->
				src.request(MAX_TEXT_BYTES + 1)
				if (src.buffer.size > MAX_TEXT_BYTES) throw IOException("This link is too large to be a playlist")
				src.buffer.readUtf8()
			}.orEmpty()
		}
	}

	private companion object {

		const val MAX_TEXT_BYTES = 2L * 1024 * 1024
	}
}
