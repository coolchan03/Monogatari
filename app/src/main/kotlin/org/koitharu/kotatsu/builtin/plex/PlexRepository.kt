package org.koitharu.kotatsu.builtin.plex

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import org.json.JSONException
import java.io.IOException
import java.util.UUID

/**
 * The user's own Plex Media Server through its HTTP API with their Plex token (More > API keys & accounts). Movies and
 * shows are titles, episodes are chapters, and playback is the server's direct file stream with the token sent as a
 * header. No covers are shown, because Plex image links would have to carry the token in the address.
 */
class PlexRepository(
	cache: MemoryContentCache,
	private val client: OkHttpClient,
	private val keys: ApiKeys,
) : CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.PLEX
	override val sortOrders = setOf(SortOrder.NEWEST)
	override var defaultSortOrder = SortOrder.NEWEST
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		if (!keys.isConfigured(ApiServices.PLEX) || offset > 0) return emptyList()
		val (server, token) = credentials()
		val term = filter?.query?.trim().orEmpty()
		val url = if (term.isEmpty()) recentUrl(server) else searchUrl(server, term)
		return parseItems(fetch(url, token)).map { it.toManga(server) }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val (server, token) = credentials()
		val (type, id) = splitUrl(manga.url) ?: throw IOException("Invalid Plex item")
		val episodes = if (type == "show") {
			parseEpisodes(fetch("$server/library/metadata/$id/allLeaves", token))
		} else {
			val part = partKey(fetch("$server/library/metadata/$id", token)) ?: throw IOException("This Plex item has no playable file")
			listOf(Episode(manga.title, part, 1f, id))
		}
		return manga.copy(chapters = episodes.map { ep ->
			MangaChapter(
				id = ArchiveMapping.uid(source, "/part${ep.partKey}"),
				title = ep.title,
				number = ep.number,
				volume = 0,
				// The metadata id is carried in the chapter URL fragment for optional server-side transcoding.
				// Fragments are never sent to the part endpoint or persisted in a token-bearing URL.
				url = if (ep.metadataId != null) "${ep.partKey}#item=${ep.metadataId}" else ep.partKey,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		})
	}

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		val (server, token) = credentials()
		val path = chapter.url.substringBefore('#')
		require(path.startsWith("/library/parts/") && !path.contains("..") && '?' !in path) { "Invalid Plex file" }
		val headers = mapOf("X-Plex-Token" to token, "X-Plex-Product" to "Monogatari",
			"X-Plex-Client-Identifier" to "monogatari-android")
		val direct = MediaStream("$server$path", "Direct play", headers = headers)
		val id = chapter.url.substringAfter("#item=", "").takeIf { ID.matches(it) } ?: return listOf(direct)
		// Server-side HLS is only offered for an explicitly identified item on the user's own Plex server.
		val transcoded = MediaStream(transcodeUrl(server, id, UUID.randomUUID().toString()),
			"Server transcode (720p)", headers = headers, mimeType = "application/x-mpegURL")
		return listOf(direct, transcoded)
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun credentials(): Pair<String, String> {
		val server = keys.get(ApiServices.PLEX, "server")
		val token = keys.get(ApiServices.PLEX, "token")
		if (server == null || token == null) {
			throw IOException("Plex isn't set up yet. Open More > API keys & accounts and add your server address and token.")
		}
		return server to token
	}

	private fun Item.toManga(server: String) = Manga(
		id = ArchiveMapping.uid(source, "/plex/$type/$id"),
		title = title,
		altTitles = emptySet(),
		url = "/$type/$id",
		publicUrl = "$server/web/index.html",
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		largeCoverUrl = null,
		description = descriptionWithYear(year, summary),
		chapters = null,
		source = source,
	)

	private suspend fun fetch(url: String, token: String): JSONObject = withContext(Dispatchers.IO) {
		val request = Request.Builder().url(url).header("X-Plex-Token", token).header("Accept", "application/json").build()
		val response = try {
			client.newCall(request).execute()
		} catch (e: IOException) {
			// Keep the cause: source health uses it to tell an offline phone from a dead server.
			throw IOException(unreachableMessage(request.url.host), e)
		}
		response.use {
			httpErrorMessage(response.code)?.let { throw IOException(it) }
			val body = response.body?.string().orEmpty().ifEmpty { "{}" }
			try {
				JSONObject(body)
			} catch (e: JSONException) {
				throw IOException(NOT_PLEX_MESSAGE, e)
			}
		}
	}

	data class Item(val id: String, val type: String, val title: String, val year: Int?, val summary: String?)

	data class Episode(val title: String, val partKey: String, val number: Float, val metadataId: String? = null)

	companion object {

		private val ID = Regex("\\d{1,12}")

		const val NOT_PLEX_MESSAGE = "The server address answered, but not like a Plex Media Server. " +
			"Check the address and port (usually 32400) in More > API keys & accounts."

		/** User-facing explanation for a failed Plex API response, or null when the request succeeded. */
		fun httpErrorMessage(code: Int): String? = when {
			code in 200..299 -> null
			code == 401 -> "Plex rejected the token. Check it in More > API keys & accounts."
			code == 403 -> "Plex refused access (HTTP 403). This account may not have access to that library."
			code == 404 -> "That item is no longer on your Plex server, or the library was moved (HTTP 404)."
			code >= 500 -> "Your Plex server reported an error (HTTP $code). Try again after it finishes starting or scanning."
			else -> "Plex request failed (HTTP $code)."
		}

		fun unreachableMessage(host: String): String =
			"Can't reach your Plex server at $host. Check that it is running; a local address (192.168.x.x) " +
				"only works on the same Wi-Fi network."

		/** Structured Plex year, not an inferred title year or a fake author. */
		fun descriptionWithYear(year: Int?, summary: String?): String? =
			listOfNotNull(
				year?.takeIf { it in 1000..2100 }?.let { "Release year: $it" },
				summary?.trim()?.takeIf(String::isNotBlank),
			).joinToString(" - ").ifBlank { null }
		private val TYPES = setOf("movie", "show")

		/** Plex universal transcoder: session-specific, header-authenticated, never embeds user tokens in URLs. */
		fun transcodeUrl(server: String, metadataId: String, session: String): String {
			require(ID.matches(metadataId)) { "Invalid Plex metadata id" }
			require(UUID_PATTERN.matches(session)) { "Invalid Plex session" }
			return "$server/video/:/transcode/universal/start.m3u8?path=${ArchiveMapping.encode("/library/metadata/$metadataId")}" +
				"&mediaIndex=0&partIndex=0&protocol=hls&directPlay=0&directStream=1" +
				"&videoQuality=60&videoResolution=1280x720&maxVideoBitrate=4000&session=$session"
		}

		private val UUID_PATTERN = Regex("[a-fA-F0-9-]{36}")

		fun recentUrl(server: String) = "$server/library/recentlyAdded?X-Plex-Container-Start=0&X-Plex-Container-Size=40"

		fun searchUrl(server: String, term: String) = "$server/hubs/search?limit=30&query=${ArchiveMapping.encode(term)}"

		fun splitUrl(url: String): Pair<String, String>? {
			val parts = url.trim('/').split('/')
			return if (parts.size == 2 && parts[0] in TYPES && ID.matches(parts[1])) parts[0] to parts[1] else null
		}

		/** Movies and shows from a recently-added list or a search (hubs); anything else (tracks, photos, ...) is skipped. */
		fun parseItems(body: JSONObject): List<Item> {
			val container = body.optJSONObject("MediaContainer") ?: return emptyList()
			val raw = ArrayList<JSONObject>()
			container.optJSONArray("Metadata")?.let { a -> for (i in 0 until a.length()) a.optJSONObject(i)?.let(raw::add) }
			container.optJSONArray("Hub")?.let { hubs ->
				for (h in 0 until hubs.length()) {
					val metadata = hubs.optJSONObject(h)?.optJSONArray("Metadata") ?: continue
					for (i in 0 until metadata.length()) metadata.optJSONObject(i)?.let(raw::add)
				}
			}
			return raw.mapNotNull { item ->
				val type = item.optString("type")
				val id = item.optString("ratingKey")
				if (type !in TYPES || !ID.matches(id)) return@mapNotNull null
				Item(
					id, type,
					item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
					item.optInt("year", 0).takeIf { it in 1000..2100 },
					item.optString("summary").takeIf { it.isNotBlank() },
				)
			}.distinctBy { it.id }
		}

		/** The file key of the first media part: "/library/parts/123/456/file.mkv". */
		fun partKey(body: JSONObject): String? =
			body.optJSONObject("MediaContainer")?.optJSONArray("Metadata")?.optJSONObject(0)?.let(::firstPart)

		private fun firstPart(item: JSONObject): String? =
			item.optJSONArray("Media")?.optJSONObject(0)?.optJSONArray("Part")?.optJSONObject(0)
				?.optString("key")?.takeIf { it.startsWith("/library/parts/") }

		fun parseEpisodes(body: JSONObject): List<Episode> {
			val items = body.optJSONObject("MediaContainer")?.optJSONArray("Metadata") ?: return emptyList()
			val parsed = (0 until items.length()).mapNotNull { i ->
				val item = items.optJSONObject(i) ?: return@mapNotNull null
				val part = firstPart(item) ?: return@mapNotNull null
				val ratingKey = item.optString("ratingKey").takeIf { ID.matches(it) }
				Triple(item.optInt("parentIndex", 0), item.optInt("index", 0), Triple(item.optString("title"), part, ratingKey))
			}.sortedWith(compareBy({ it.first }, { it.second }))
			return parsed.mapIndexed { i, (season, number, titlePart) ->
				val label = if (season > 0 && number > 0) "S$season E$number" else "Episode ${i + 1}"
				val name = titlePart.first.takeIf { it.isNotBlank() }
				Episode(if (name != null) "$label · $name" else label, titlePart.second, (i + 1).toFloat(), titlePart.third)
			}
		}
	}
}
