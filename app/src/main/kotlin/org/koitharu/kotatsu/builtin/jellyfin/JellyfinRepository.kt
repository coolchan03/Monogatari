package org.koitharu.kotatsu.builtin.jellyfin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
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
 * The user's own Jellyfin server through its documented REST API, authenticated with an API key created in the
 * server's dashboard. Movies and series show up as titles; a series' episodes are its chapters. Playback is the
 * server's direct stream, sent with the key in a header (never in a URL that is stored or shared).
 */
class JellyfinRepository(
	cache: MemoryContentCache,
	private val client: OkHttpClient,
	private val config: JellyfinConfig,
) : CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.JELLYFIN
	override val sortOrders = setOf(SortOrder.ALPHABETICAL, SortOrder.NEWEST)
	override var defaultSortOrder = SortOrder.ALPHABETICAL
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val (server, key) = credentials()
		val url = itemsUrl(server, filter?.query?.trim().orEmpty(), offset.coerceAtLeast(0), order == SortOrder.NEWEST)
		return parseItems(fetch(url, key)).map { it.toManga(server) }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val (server, key) = credentials()
		val id = manga.url.substringAfterLast('/')
		require(ID.matches(id)) { "Invalid Jellyfin item id" }
		val item = JSONObject(fetchText("$server/Items/$id", key))
		val chapters = if (item.optString("Type") == "Series") {
			parseEpisodes(fetch(episodesUrl(server, id), key))
		} else {
			listOf(Episode(id, manga.title, 1f))
		}
		return manga.copy(
			chapters = chapters.map { ep ->
				MangaChapter(
					id = ArchiveMapping.uid(source, "/episode/${ep.id}"),
					title = ep.title,
					number = ep.number,
					volume = 0,
					url = ep.id,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				)
			},
		)
	}

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		val (server, key) = credentials()
		val id = chapter.url.substringAfterLast('/')
		require(ID.matches(id)) { "Invalid Jellyfin item id" }
		return listOf(
			MediaStream(
				url = streamUrl(server, id),
				quality = "Direct stream",
				headers = mapOf(AUTH_HEADER to key),
			),
		)
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun credentials(): Pair<String, String> {
		val server = config.server
		val key = config.apiKey
		if (server == null || key == null) {
			throw IOException("Jellyfin isn't set up yet. Open More > Jellyfin server and enter your server address and API key.")
		}
		return server to key
	}

	private fun Item.toManga(server: String) = Manga(
		id = ArchiveMapping.uid(source, "/item/$id"),
		title = title,
		altTitles = emptySet(),
		url = "/Items/$id",
		publicUrl = "$server/web/index.html#!/details?id=$id",
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = if (hasImage) "$server/Items/$id/Images/Primary?maxWidth=400" else null,
		tags = emptySet(),
		state = null,
		authors = setOfNotNull(year?.toString()),
		largeCoverUrl = null,
		description = overview,
		chapters = null,
		source = source,
	)

	private suspend fun fetchText(url: String, key: String): String = withContext(Dispatchers.IO) {
		val request = Request.Builder().url(url).header(AUTH_HEADER, key).build()
		client.newCall(request).execute().use { response ->
			when {
				response.code == 401 -> throw IOException("Jellyfin rejected the API key. Create a new one in the server dashboard.")
				!response.isSuccessful -> throw IOException("Jellyfin HTTP ${response.code}")
			}
			response.body?.string().orEmpty().ifEmpty { "{}" }
		}
	}

	private suspend fun fetch(url: String, key: String) = JSONObject(fetchText(url, key))

	data class Item(val id: String, val title: String, val year: Int?, val overview: String?, val hasImage: Boolean)

	data class Episode(val id: String, val title: String, val number: Float)

	companion object {

		const val PAGE_SIZE = 30
		const val AUTH_HEADER = "X-Emby-Token"
		private val ID = Regex("[0-9a-fA-F]{32}|[0-9a-fA-F-]{36}")

		/**
		 * Accepts what people type ("192.168.1.5:8096", "jellyfin.example.com", "https://x/") and returns a clean base
		 * URL without a trailing slash, or null if it is not an http(s) address. Bare local addresses default to
		 * http because home servers rarely have certificates; everything else defaults to https.
		 */
		fun normalizeServer(input: String): String? {
			if (input.trim().endsWith("://")) return null
			val text = input.trim().trimEnd('/')
			if (text.isEmpty() || text.any { it.isWhitespace() }) return null
			val withScheme = when {
				text.startsWith("http://", true) || text.startsWith("https://", true) -> text
				"://" in text -> return null
				isLocalHost(text.substringBefore('/').substringBefore(':')) -> "http://$text"
				else -> "https://$text"
			}
			val host = withScheme.substringAfter("://").substringBefore('/').substringBefore(':')
			return if (host.isEmpty()) null else withScheme
		}

		private fun isLocalHost(host: String): Boolean =
			host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || '.' !in host ||
				Regex("\\d{1,3}(\\.\\d{1,3}){3}").matches(host)

		fun itemsUrl(server: String, term: String, offset: Int, newestFirst: Boolean): String {
			val sort = if (newestFirst) "DateCreated&SortOrder=Descending" else "SortName"
			val search = if (term.isEmpty()) "" else "&SearchTerm=${ArchiveMapping.encode(term)}"
			return "$server/Items?Recursive=true&IncludeItemTypes=Movie,Series&Fields=Overview,ProductionYear" +
				"&Limit=$PAGE_SIZE&StartIndex=$offset&SortBy=$sort$search"
		}

		fun episodesUrl(server: String, seriesId: String) =
			"$server/Shows/$seriesId/Episodes?Fields=Overview"

		fun streamUrl(server: String, id: String) = "$server/Videos/$id/stream?static=true"

		fun parseItems(body: JSONObject): List<Item> {
			val items = body.optJSONArray("Items") ?: return emptyList()
			return (0 until items.length()).mapNotNull { i ->
				val item = items.optJSONObject(i) ?: return@mapNotNull null
				val id = item.optString("Id").takeIf { ID.matches(it) } ?: return@mapNotNull null
				Item(
					id = id,
					title = item.optString("Name").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
					year = item.optInt("ProductionYear", 0).takeIf { it > 0 },
					overview = item.optString("Overview").takeIf { it.isNotBlank() },
					hasImage = item.optJSONObject("ImageTags")?.has("Primary") == true,
				)
			}
		}

		/** Episodes ordered by season then episode, labelled "S1 E3 · Title". */
		fun parseEpisodes(body: JSONObject): List<Episode> {
			val items = body.optJSONArray("Items") ?: return emptyList()
			val parsed = (0 until items.length()).mapNotNull { i ->
				val item = items.optJSONObject(i) ?: return@mapNotNull null
				val id = item.optString("Id").takeIf { ID.matches(it) } ?: return@mapNotNull null
				val season = item.optInt("ParentIndexNumber", 0)
				val number = item.optInt("IndexNumber", 0)
				Triple(season, number, Pair(id, item.optString("Name")))
			}.sortedWith(compareBy({ it.first }, { it.second }))
			return parsed.mapIndexed { index, (season, number, idName) ->
				val label = if (season > 0 && number > 0) "S$season E$number" else "Episode ${index + 1}"
				val name = idName.second.takeIf { it.isNotBlank() }
				Episode(idName.first, if (name != null) "$label · $name" else label, (index + 1).toFloat())
			}
		}
	}
}
