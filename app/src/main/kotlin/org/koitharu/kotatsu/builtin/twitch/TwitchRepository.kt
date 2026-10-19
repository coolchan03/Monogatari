package org.koitharu.kotatsu.builtin.twitch

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
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
import java.io.IOException

/**
 * Twitch channels, live streams and past broadcasts through Twitch's official Helix API with the user's own app
 * credentials (More > API keys & accounts). Twitch offers no official stream URL, so playback is linked out to twitch.tv.
 */
class TwitchRepository(
	cache: MemoryContentCache,
	private val client: OkHttpClient,
	private val keys: ApiKeys,
) : CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.TWITCH
	override val sortOrders = setOf(SortOrder.POPULARITY)
	override var defaultSortOrder = SortOrder.POPULARITY
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	@Volatile
	private var token: Pair<String, Long>? = null

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		if (!keys.isConfigured(ApiServices.TWITCH) || offset > 0) return emptyList()
		val term = filter?.query?.trim().orEmpty()
		val channels = if (term.isEmpty()) {
			parseStreams(helix("streams?first=20"))
		} else {
			parseChannels(helix("search/channels?first=20&query=${ArchiveMapping.encode(term)}"))
		}
		return channels.map { it.toManga() }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val (login, id) = splitUrl(manga.url) ?: throw IOException("Invalid Twitch channel")
		val videos = if (id != null) parseVideos(helix("videos?first=20&type=archive&user_id=$id")) else emptyList()
		val clips = if (id != null) try {
			parseClips(helix("clips?broadcaster_id=$id&first=20"))
		} catch (_: IOException) { emptyList() } else emptyList()
		val chapters = buildList {
			add(Video("live", "Live channel (opens twitch.tv)", "https://www.twitch.tv/$login"))
			addAll(videos)
			addAll(clips)
		}
		return manga.copy(chapters = chapters.mapIndexed { index, v ->
			MangaChapter(
				id = ArchiveMapping.uid(source, "/video/${v.url}"),
				title = v.title,
				number = (index + 1).toFloat(),
				volume = 0,
				url = v.url,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		})
	}

	/** Twitch has no official stream URL, so say clearly where to watch instead of failing silently. */
	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		throw IOException("Twitch only allows playback on twitch.tv. Open ${chapter.url} in your browser (Open in browser on the title page).")
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun Channel.toManga() = Manga(
		id = ArchiveMapping.uid(source, "/twitch/$login"),
		title = name,
		altTitles = emptySet(),
		url = "/channel/$login" + (id?.let { "/$it" } ?: ""),
		publicUrl = "https://www.twitch.tv/$login",
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = thumbnail,
		tags = emptySet(),
		state = null,
		authors = setOfNotNull(game),
		largeCoverUrl = null,
		description = title,
		chapters = null,
		source = source,
	)

	private suspend fun appToken(): String {
		token?.takeIf { it.second > System.currentTimeMillis() + 60_000L }?.let { return it.first }
		val clientId = keys.get(ApiServices.TWITCH, "client_id")
		val secret = keys.get(ApiServices.TWITCH, "client_secret")
		if (clientId == null || secret == null) throw IOException("Twitch isn't set up yet. Open More > API keys & accounts and add your Client ID and secret.")
		val fresh = withContext(Dispatchers.IO) {
			val body = FormBody.Builder().add("client_id", clientId).add("client_secret", secret).add("grant_type", "client_credentials").build()
			client.newCall(Request.Builder().url("https://id.twitch.tv/oauth2/token").post(body).build()).execute().use { response ->
				if (response.code == 400 || response.code == 401) throw IOException("Twitch rejected the Client ID or secret. Check them in More > API keys & accounts.")
				if (!response.isSuccessful) throw IOException("Twitch HTTP ${response.code}")
				val json = JSONObject(response.body?.string().orEmpty().ifEmpty { "{}" })
				json.optString("access_token") to System.currentTimeMillis() + json.optLong("expires_in", 3600L) * 1000L
			}
		}
		token = fresh
		return fresh.first
	}

	private suspend fun helix(path: String): JSONObject {
		val clientId = keys.get(ApiServices.TWITCH, "client_id") ?: throw IOException("Twitch isn't set up yet")
		val bearer = appToken()
		return withContext(Dispatchers.IO) {
			val request = Request.Builder().url("https://api.twitch.tv/helix/$path")
				.header("Client-Id", clientId).header("Authorization", "Bearer $bearer").build()
			client.newCall(request).execute().use { response ->
				if (response.code == 401) { token = null; throw IOException("Twitch refused the request. Try again.") }
				if (!response.isSuccessful) throw IOException("Twitch HTTP ${response.code}")
				JSONObject(response.body?.string().orEmpty().ifEmpty { "{}" })
			}
		}
	}

	data class Channel(val id: String?, val login: String, val name: String, val title: String?, val game: String?, val thumbnail: String?)

	data class Video(val id: String, val title: String, val url: String)

	companion object {

		private val LOGIN = Regex("[A-Za-z0-9_]{1,40}")
		private val ID = Regex("\\d{1,15}")

		private fun https(url: String?): String? = url?.takeIf { it.startsWith("https://") }
			?.replace("{width}", "640")?.replace("{height}", "360")?.replace("%{width}", "640")?.replace("%{height}", "360")

		fun splitUrl(url: String): Pair<String, String?>? {
			val parts = url.trim('/').split('/')
			if (parts.size !in 2..3 || parts[0] != "channel" || !LOGIN.matches(parts[1])) return null
			val id = parts.getOrNull(2)
			return if (id == null || ID.matches(id)) parts[1] to id else null
		}

		fun parseChannels(body: JSONObject): List<Channel> {
			val data = body.optJSONArray("data") ?: return emptyList()
			return (0 until data.length()).mapNotNull { i ->
				val c = data.optJSONObject(i) ?: return@mapNotNull null
				val login = c.optString("broadcaster_login").takeIf { LOGIN.matches(it) } ?: return@mapNotNull null
				Channel(
					c.optString("id").takeIf { ID.matches(it) }, login,
					c.optString("display_name").ifBlank { login },
					c.optString("title").takeIf { it.isNotBlank() }, c.optString("game_name").takeIf { it.isNotBlank() },
					https(c.optString("thumbnail_url")),
				)
			}
		}

		/** Top live streams: the channel id comes from user_id. */
		fun parseStreams(body: JSONObject): List<Channel> {
			val data = body.optJSONArray("data") ?: return emptyList()
			return (0 until data.length()).mapNotNull { i ->
				val s = data.optJSONObject(i) ?: return@mapNotNull null
				val login = s.optString("user_login").takeIf { LOGIN.matches(it) } ?: return@mapNotNull null
				Channel(
					s.optString("user_id").takeIf { ID.matches(it) }, login,
					s.optString("user_name").ifBlank { login },
					s.optString("title").takeIf { it.isNotBlank() }, s.optString("game_name").takeIf { it.isNotBlank() },
					https(s.optString("thumbnail_url")),
				)
			}
		}

		/** Public Helix clip search. Use validated IDs to generate official playback URLs. */
		fun parseClips(body: JSONObject): List<Video> {
			val data = body.optJSONArray("data") ?: return emptyList()
			val clipIds = Regex("[A-Za-z0-9_-]{2,130}")
			return (0 until data.length()).mapNotNull { index ->
				val clip = data.optJSONObject(index) ?: return@mapNotNull null
				val id = clip.optString("id").takeIf { clipIds.matches(it) }
					?: return@mapNotNull null
				val date = clip.optString("created_at").take(10)
				val title = clip.optString("title").ifBlank { "Highlight clip" }.take(180)
				Video(id,
					(if (date.isBlank()) "" else "$date - ") + "Clip: " + title,
					"https://clips.twitch.tv/$id",
				)
			}.distinctBy { it.id }
		}

		fun parseVideos(body: JSONObject): List<Video> {
			val data = body.optJSONArray("data") ?: return emptyList()
			return (0 until data.length()).mapNotNull { i ->
				val v = data.optJSONObject(i) ?: return@mapNotNull null
				val id = v.optString("id").takeIf { ID.matches(it) } ?: return@mapNotNull null
				val title = v.optString("title").ifBlank { "Broadcast $id" }
				val date = v.optString("published_at").take(10)
				Video(id, if (date.isNotBlank()) "$date · $title" else title, "https://www.twitch.tv/videos/$id")
			}
		}
	}
}
