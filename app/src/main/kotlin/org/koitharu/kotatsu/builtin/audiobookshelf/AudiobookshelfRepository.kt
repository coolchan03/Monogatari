package org.koitharu.kotatsu.builtin.audiobookshelf

import okhttp3.OkHttpClient
import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.keys.ApiKeys
import org.koitharu.kotatsu.core.keys.ApiServices
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.IOException

/**
 * The user's own Audiobookshelf server through its REST API, with the API token from the web app's user settings
 * (More > API keys & accounts). Audiobooks and podcasts are titles; audio files and podcast episodes are chapters.
 * Playback is the server's file stream with the token sent as a header (never in a stored URL). No covers are shown,
 * because Audiobookshelf covers need the token, which must not end up in saved library data. Listening progress is
 * not reported back, matching the Jellyfin and Plex sources.
 */
class AudiobookshelfRepository(
	cache: MemoryContentCache,
	client: OkHttpClient,
	private val keys: ApiKeys,
) : CachingMangaRepository(cache), ContinuousMediaProvider {

	private val client = AudiobookshelfApi.withTimeout(client)

	override val source = BuiltinSource.AUDIOBOOKSHELF
	override val sortOrders = setOf(SortOrder.NEWEST)
	override var defaultSortOrder = SortOrder.NEWEST
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		// Not set up yet: no results rather than an error, so global search isn't noisy for people without a server.
		if (!keys.isConfigured(ApiServices.AUDIOBOOKSHELF)) return emptyList()
		val (server, token) = credentials()
		val libraries = AudiobookshelfApi.parseLibraries(AudiobookshelfApi.get(client, AudiobookshelfApi.librariesUrl(server), token))
		val term = filter?.query?.trim().orEmpty()
		val items = if (term.isNotEmpty()) {
			if (offset > 0) return emptyList()
			libraries.flatMap { lib ->
				AudiobookshelfApi.parseSearch(AudiobookshelfApi.get(client, AudiobookshelfApi.searchUrl(server, lib.id, term), token))
			}
		} else {
			browse(server, token, libraries, offset.coerceAtLeast(0))
		}
		return items.distinctBy { it.id }.map { it.toManga(server) }
	}

	/** Libraries are treated as one list in order: pages walk through the first library, then the next. */
	private suspend fun browse(
		server: String,
		token: String,
		libraries: List<AudiobookshelfApi.Library>,
		offset: Int,
	): List<AudiobookshelfApi.Item> {
		var skip = offset
		for (library in libraries) {
			val page = skip / AudiobookshelfApi.PAGE_SIZE
			val body = AudiobookshelfApi.get(client, AudiobookshelfApi.itemsUrl(server, library.id, page), token)
			val total = body.optInt("total", 0)
			if (skip >= total) {
				skip -= total
				continue
			}
			return AudiobookshelfApi.parseItems(body).drop(skip - page * AudiobookshelfApi.PAGE_SIZE)
		}
		return emptyList()
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val (server, token) = credentials()
		val id = manga.url.substringAfterLast('/')
		require(AudiobookshelfApi.ID.matches(id)) { "Invalid Audiobookshelf item id" }
		val details = AudiobookshelfApi.parseDetails(
			AudiobookshelfApi.get(client, AudiobookshelfApi.itemUrl(server, id), token),
		) ?: throw IOException("This Audiobookshelf item has no details")
		return manga.copy(
			authors = details.authors.toSet().ifEmpty { manga.authors },
			description = description(details.item) ?: manga.description,
			tags = details.item.genres.map { MangaTag(it, it, source) }.toSet(),
			chapters = details.tracks.map { track ->
				MangaChapter(
					id = ArchiveMapping.uid(source, "/track/$id/${track.ino}"),
					title = track.title,
					number = track.number,
					volume = 0,
					url = "$id/${track.ino}",
					scanlator = null,
					uploadDate = track.publishedAt,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		val (server, token) = credentials()
		val parts = chapter.url.split('/')
		require(parts.size == 2 && parts.all { AudiobookshelfApi.ID.matches(it) }) { "Invalid Audiobookshelf track" }
		return listOf(
			MediaStream(
				url = AudiobookshelfApi.fileUrl(server, parts[0], parts[1]),
				quality = "Original audio",
				headers = AudiobookshelfApi.authHeaders(token),
			),
		)
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun credentials(): Pair<String, String> {
		val server = keys.get(ApiServices.AUDIOBOOKSHELF, "server")
		val token = keys.get(ApiServices.AUDIOBOOKSHELF, "token")
		if (server == null || token == null) {
			throw IOException("Audiobookshelf isn't set up yet. Open More > API keys & accounts and add your server address and API token.")
		}
		return server to token
	}

	private fun description(item: AudiobookshelfApi.Item): String? =
		listOfNotNull(item.narrator?.let { "Narrated by $it" }, item.description).joinToString("\n\n").ifBlank { null }

	private fun AudiobookshelfApi.Item.toManga(server: String) = Manga(
		id = ArchiveMapping.uid(source, "/item/$id"),
		title = title,
		altTitles = emptySet(),
		url = "/item/$id",
		publicUrl = "$server/item/$id",
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = setOfNotNull(author),
		largeCoverUrl = null,
		description = description(this),
		chapters = null,
		source = source,
	)
}
