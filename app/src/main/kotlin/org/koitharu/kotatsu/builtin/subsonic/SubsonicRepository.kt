package org.koitharu.kotatsu.builtin.subsonic

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
 * The user's own Subsonic-compatible music server (Navidrome, Gonic, Airsonic, Subsonic) through the Subsonic REST
 * API. Albums are titles and their songs are chapters. Every request is signed with a fresh salted token, so the
 * password is never sent. The stream address carries that token because the Subsonic API has no header login; it is
 * built when playback starts and never stored. No covers are shown, since cover addresses would have to carry the
 * token into saved library data. Scrobbling is not reported.
 */
class SubsonicRepository(
	cache: MemoryContentCache,
	client: OkHttpClient,
	private val keys: ApiKeys,
) : CachingMangaRepository(cache), ContinuousMediaProvider {

	private val client = SubsonicApi.withTimeout(client)

	override val source = BuiltinSource.SUBSONIC
	override val sortOrders = setOf(SortOrder.NEWEST, SortOrder.ALPHABETICAL)
	override var defaultSortOrder = SortOrder.NEWEST
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		// Not set up yet: no results rather than an error, so global search isn't noisy for people without a server.
		if (!keys.isConfigured(ApiServices.SUBSONIC)) return emptyList()
		val login = credentials()
		val term = filter?.query?.trim().orEmpty()
		val albums = if (term.isEmpty()) {
			val params = SubsonicApi.albumListParams(order != SortOrder.ALPHABETICAL, offset)
			SubsonicApi.parseAlbumList(call(login, "getAlbumList2", params))
		} else {
			SubsonicApi.parseSearch(call(login, "search3", SubsonicApi.searchParams(term, offset)))
		}
		return albums.map { it.toManga(login.server) }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val login = credentials()
		val id = manga.url.substringAfterLast('/')
		require(SubsonicApi.ID.matches(id)) { "Invalid Subsonic album id" }
		val details = SubsonicApi.parseAlbumDetails(call(login, "getAlbum", listOf("id" to id)))
			?: throw IOException("This album has no details on your server")
		val multiDisc = details.songs.any { it.disc > 1 }
		return manga.copy(
			authors = setOfNotNull(details.album.artist).ifEmpty { manga.authors },
			tags = setOfNotNull(details.album.genre).map { MangaTag(it, it, source) }.toSet(),
			chapters = details.songs.mapIndexed { index, song ->
				MangaChapter(
					id = ArchiveMapping.uid(source, "/song/${song.id}"),
					title = SubsonicApi.chapterTitle(song, multiDisc, index),
					number = (index + 1).toFloat(),
					volume = 0,
					url = song.id,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		val login = credentials()
		val id = chapter.url.substringAfterLast('/')
		require(SubsonicApi.ID.matches(id)) { "Invalid Subsonic song id" }
		val url = SubsonicApi.url(login.server, "stream", listOf("id" to id), login.user, login.password)
		return listOf(MediaStream(url = url, quality = "Original audio"))
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private class Login(val server: String, val user: String, val password: String)

	private suspend fun call(login: Login, method: String, params: List<Pair<String, String>>) =
		SubsonicApi.call(client, login.server, method, params, login.user, login.password)

	private fun credentials(): Login {
		val server = keys.get(ApiServices.SUBSONIC, "server")
		val user = keys.get(ApiServices.SUBSONIC, "username")
		val password = keys.get(ApiServices.SUBSONIC, "password")
		if (server == null || user == null || password == null) {
			throw IOException("Subsonic isn't set up yet. Open More > API keys & accounts and add your server address, username and password.")
		}
		return Login(server, user, password)
	}

	private fun SubsonicApi.Album.toManga(server: String) = Manga(
		id = ArchiveMapping.uid(source, "/album/$id"),
		title = name,
		altTitles = emptySet(),
		url = "/album/$id",
		publicUrl = server,
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = setOfNotNull(artist),
		largeCoverUrl = null,
		description = listOfNotNull(year?.let { "Release year: $it" }, genre).joinToString(" - ").ifBlank { null },
		chapters = null,
		source = source,
	)
}
