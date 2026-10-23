package org.koitharu.kotatsu.discover

import android.content.Context
import androidx.annotation.StringRes
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

/**
 * Read-only AniList discovery lists (no account needed, adult titles included). Tapping a result searches the user's
 * own sources. When AniList fails (rate limit, Cloudflare block, outage) [load] falls back to MyAnimeList via Jikan.
 */
class AniListDiscover @Inject constructor(
	@BaseHttpClient private val client: OkHttpClient,
	@ApplicationContext private val context: Context,
) {

	private val jikan = JikanDiscover(client)

	enum class Feed(
		@StringRes val titleRes: Int,
		val type: String,
		val sort: String,
		val tag: String? = null,
		val format: String? = null,
		/** `null` returns adult and non-adult titles alike; `true` only adult ones. */
		val adult: Boolean? = null,
	) {
		TRENDING_MANGA(R.string.discover_trending_manga, "MANGA", "TRENDING_DESC"),
		TRENDING_BL(R.string.discover_trending_bl, "MANGA", "TRENDING_DESC", tag = "Boys' Love"),
		POPULAR_BL(R.string.discover_popular_bl, "MANGA", "POPULARITY_DESC", tag = "Boys' Love"),
		ADULT_BL(R.string.discover_adult_bl, "MANGA", "TRENDING_DESC", tag = "Boys' Love", adult = true),
		TRENDING_ANIME(R.string.discover_trending_anime, "ANIME", "TRENDING_DESC"),
		BL_ANIME(R.string.discover_bl_anime, "ANIME", "POPULARITY_DESC", tag = "Boys' Love"),
		LIGHT_NOVELS(R.string.discover_light_novels, "MANGA", "POPULARITY_DESC", format = "NOVEL"),
	}

	data class Item(
		val id: Int,
		val title: String,
		val alternativeTitle: String?,
		val coverUrl: String?,
		val score: Int?,
		val isAdult: Boolean = false,
		/** Public catalog cards open legal provider listings; AniList cards retain global search. */
		val officialUrl: String? = null,
		val providerLabel: String? = null,
		/** AniList genres, used by the on-device "For you" ranking. */
		val genres: List<String> = emptyList(),
		/** Set for titles listed straight from a built-in source; a tap opens their details page in the app. */
		val manga: org.koitharu.kotatsu.parsers.model.Manga? = null,
		/** AniList media id of an "Airing this week" card ([id] is per episode there); `null` for other cards. */
		val aniListMediaId: Int? = null,
		/** Stable identity for de-duplication; [id] alone collides for airing cards (one per show and episode). */
		val uniqueKey: String = id.toString(),
	)

	class Page(val items: List<Item>, val hasNextPage: Boolean)

	/** One aired episode of a followed show, for airing reminders. [airingAt] is in epoch seconds. */
	data class AiredEpisode(val mediaId: Int, val title: String, val episode: Int, val airingAt: Long)

	class FollowedAiringPage(val episodes: List<AiredEpisode>, val hasNextPage: Boolean)

	/**
	 * One page of [feed]. Page 1 tries AniList first and switches to MyAnimeList (Jikan) if AniList throws; later
	 * pages stay with the provider that served page 1 so paging never mixes the two catalogs.
	 */
	suspend fun load(feed: Feed, page: Int): Page = withContext(Dispatchers.IO) {
		val key = cacheKey(feed, if (feed in fallbackFeeds) Provider.JIKAN else Provider.ANILIST, page)
		pageCache[key]?.takeIf { it.expiresAt > System.currentTimeMillis() }?.let { return@withContext it.page }
		val result = if (page > 1 && feed in fallbackFeeds) {
			jikan.load(feed, page)
		} else {
			try {
				parse(postAniList(buildRequestBody(feed, page))).also {
					if (page == 1) fallbackFeeds.remove(feed)
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				if (page > 1) throw IOException(reason(e), e)
				val fallback = try {
					jikan.load(feed, page)
				} catch (j: CancellationException) {
					throw j
				} catch (j: Exception) {
					throw IOException(context.getString(R.string.discover_fallback_failed, reason(e), reason(j)), e)
				}
				fallbackFeeds.add(feed)
				fallback
			}
		}
		// The provider may have changed while loading (fallback), so the entry is stored under the one that served it.
		val provider = if (feed in fallbackFeeds) Provider.JIKAN else Provider.ANILIST
		val ttl = if (provider == Provider.JIKAN) FALLBACK_CACHE_TTL_MS else CACHE_TTL_MS
		pageCache[cacheKey(feed, provider, page)] = CachedPage(result, System.currentTimeMillis() + ttl)
		result
	}

	/** POSTs a GraphQL body to AniList; retries once after a short 429 and reports AniList's own error text. */
	private suspend fun postAniList(payload: JSONObject): String {
		var attempt = 0
		while (true) {
			try {
				val request = Request.Builder()
					.url(ENDPOINT)
					.header("Accept", "application/json")
					.header("User-Agent", USER_AGENT)
					.post(payload.toString().toRequestBody(JSON))
					.build()
				return client.newCall(request).execute().use { response ->
					val text = response.body?.string()
					if (!response.isSuccessful) {
						val reason = text?.let(::errorMessage)?.let { ": $it" }.orEmpty()
						throw IOException("AniList returned HTTP ${response.code}$reason")
					}
					text ?: throw IOException("Empty AniList response")
				}
			} catch (e: TooManyRequestExceptions) {
				val wait = e.getRetryDelay().coerceAtLeast(1_000L)
				if (attempt++ > 0 || wait > MAX_RETRY_WAIT_MS) throw e
				delay(wait)
			}
		}
	}

	/**
	 * v1.3 draft release calendar: anime episodes airing between now and [days] days ahead, soonest first.
	 * [label] formats the episode number and airing time (epoch seconds) for the card badge.
	 */
	suspend fun loadAiring(page: Int, days: Int, label: (episode: Int, airingAt: Long) -> String): Page =
		withContext(Dispatchers.IO) {
			val now = System.currentTimeMillis() / 1000
			val body = try {
				postAniList(buildAiringRequestBody(page, now, now + days * 86_400L))
			} catch (e: IOException) {
				throw IOException(reason(e), e)
			}
			parseAiring(body, label)
		}

	/** Episodes of the followed [mediaIds] that aired in the [from, to) window (epoch seconds), oldest first. */
	suspend fun loadFollowedAiring(mediaIds: Collection<Int>, from: Long, to: Long): List<AiredEpisode> =
		withContext(Dispatchers.IO) {
			if (mediaIds.isEmpty()) return@withContext emptyList()
			val result = ArrayList<AiredEpisode>()
			var page = 1
			do {
				val body = postAniList(buildFollowedAiringRequestBody(mediaIds, from, to, page))
				val parsed = parseFollowedAiring(body)
				result += parsed.episodes
				page++
			} while (parsed.hasNextPage && page <= MAX_FOLLOWED_PAGES)
			result
		}

	/** Rate limits and Cloudflare blocks carry no message of their own, so they get a translated one. */
	private fun reason(e: Throwable): String = when {
		e is TooManyRequestExceptions -> context.getString(R.string.discover_rate_limited)
		e.javaClass.simpleName.startsWith("CloudFlare") -> context.getString(R.string.discover_blocked_by_cloudflare)
		else -> describe(e)
	}

	/** Which catalog served a page; cached pages are kept per provider so the two are never mixed while paging. */
	internal enum class Provider { ANILIST, JIKAN }

	private class CachedPage(val page: Page, val expiresAt: Long)

	companion object {

		private const val MAX_FOLLOWED_PAGES = 5
		private const val MAX_RETRY_WAIT_MS = 3_000L
		private const val CACHE_TTL_MS = 10 * 60_000L
		private const val FALLBACK_CACHE_TTL_MS = 3 * 60_000L

		/** AniList tags at or above this relevance (0-100) join the genres for the "For you" ranking. */
		private const val MIN_TAG_RANK = 60
		private const val MAX_TAGS = 8

		/** Identifies the app instead of OkHttp's generic agent, which AniList's Cloudflare front may challenge. */
		val USER_AGENT = "Monogatari/${BuildConfig.VERSION_NAME} (Android)"

		/** Shared by every injected instance (Discover tab, media shelves) so screens reuse pages for a while. */
		private val pageCache = ConcurrentHashMap<String, CachedPage>()
		private val fallbackFeeds: MutableSet<Feed> = ConcurrentHashMap.newKeySet()

		internal fun cacheKey(feed: Feed, provider: Provider, page: Int): String = "${feed.name}:${provider.name}:$page"

		/** AniList's `errors[0].message` (or a plain `message`) from an error body; `null` when it isn't JSON. */
		internal fun errorMessage(body: String): String? = runCatching {
			val root = JSONObject(body)
			(root.optJSONArray("errors")?.optJSONObject(0)?.optString("message") ?: root.optString("message"))
				.takeIf { it.isNotBlank() && it != "null" }
		}.getOrNull()

		/** A readable reason for the Discover error line; several network exceptions carry no message at all. */
		internal fun describe(e: Throwable): String =
			e.message?.takeIf { it.isNotBlank() }?.take(200) ?: e.javaClass.simpleName

		private const val FOLLOWED_AIRING_QUERY = """
query (${'$'}page: Int, ${'$'}ids: [Int], ${'$'}from: Int, ${'$'}to: Int) {
  Page(page: ${'$'}page, perPage: 50) {
    pageInfo { hasNextPage }
    airingSchedules(mediaId_in: ${'$'}ids, airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
      episode
      airingAt
      media {
        id
        title { english romaji }
      }
    }
  }
}"""

		fun buildFollowedAiringRequestBody(mediaIds: Collection<Int>, from: Long, to: Long, page: Int = 1): JSONObject {
			val ids = JSONArray()
			mediaIds.distinct().sorted().forEach { ids.put(it) }
			return JSONObject()
				.put("query", FOLLOWED_AIRING_QUERY.trim())
				.put("variables", JSONObject().put("page", page).put("ids", ids).put("from", from).put("to", to))
		}

		/** Parses the followed-shows `airingSchedules` page; entries without a media id or title are skipped. */
		fun parseFollowedAiring(body: String): FollowedAiringPage {
			val root = JSONObject(body)
			root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.let { message ->
				if (message.isNotEmpty()) throw IOException(message)
			}
			val page = root.optJSONObject("data")?.optJSONObject("Page") ?: return FollowedAiringPage(emptyList(), false)
			val schedules = page.optJSONArray("airingSchedules") ?: JSONArray()
			val episodes = (0 until schedules.length()).mapNotNull { i ->
				val entry = schedules.optJSONObject(i) ?: return@mapNotNull null
				val media = entry.optJSONObject("media") ?: return@mapNotNull null
				val mediaId = media.optInt("id", 0).takeIf { it > 0 } ?: return@mapNotNull null
				val titles = media.optJSONObject("title")
				val english = titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
				val romaji = titles?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
				AiredEpisode(
					mediaId = mediaId,
					title = english ?: romaji ?: return@mapNotNull null,
					episode = entry.optInt("episode", 0),
					airingAt = entry.optLong("airingAt", 0L),
				)
			}
			val hasNext = page.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: false
			return FollowedAiringPage(episodes, hasNext)
		}

		private const val AIRING_QUERY = """
query (${'$'}page: Int, ${'$'}from: Int, ${'$'}to: Int) {
  Page(page: ${'$'}page, perPage: 40) {
    pageInfo { hasNextPage }
    airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
      episode
      airingAt
      media {
        id
        title { english romaji }
        coverImage { large }
        averageScore
        isAdult
        genres
      }
    }
  }
}"""

		fun buildAiringRequestBody(page: Int, from: Long, to: Long): JSONObject = JSONObject()
			.put("query", AIRING_QUERY.trim())
			.put("variables", JSONObject().put("page", page).put("from", from).put("to", to))

		/** Drops adult titles when the user chose to hide adult content; otherwise returns [items] unchanged. */
		fun visibleItems(items: List<Item>, hideAdult: Boolean): List<Item> =
			if (hideAdult) items.filterNot { it.isAdult } else items

		/** Collision-free identity of one airing card: `(100, 32)` and `(101, 1)` differ although `id * 31 + episode` matches. */
		fun airingKey(mediaId: Int, episode: Int): String = "$mediaId:$episode"

		/** Parses an `airingSchedules` page; each episode is its own card, badge from [label]. */
		fun parseAiring(body: String, label: (episode: Int, airingAt: Long) -> String): Page {
			val root = JSONObject(body)
			root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.let { message ->
				if (message.isNotEmpty()) throw IOException(message)
			}
			val page = root.optJSONObject("data")?.optJSONObject("Page") ?: return Page(emptyList(), false)
			val schedules = page.optJSONArray("airingSchedules") ?: JSONArray()
			val items = (0 until schedules.length()).mapNotNull { i ->
				val entry = schedules.optJSONObject(i) ?: return@mapNotNull null
				val media = entry.optJSONObject("media") ?: return@mapNotNull null
				val episode = entry.optInt("episode", 0)
				val airingAt = entry.optLong("airingAt", 0L)
				val titles = media.optJSONObject("title")
				val english = titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
				val romaji = titles?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
				val title = english ?: romaji ?: return@mapNotNull null
				Item(
					// Episode-level id: the same show appears once per episode in the week.
					id = media.optInt("id") * 31 + episode,
					uniqueKey = airingKey(media.optInt("id"), episode),
					title = title,
					alternativeTitle = romaji?.takeIf { it != title },
					coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.startsWith("https://") },
					score = null,
					isAdult = media.optBoolean("isAdult", false),
					providerLabel = label(episode, airingAt),
					genres = media.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) } }
						.orEmpty().filter { it.isNotBlank() },
					aniListMediaId = media.optInt("id", 0).takeIf { it > 0 },
				)
			}
			val hasNext = page.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: false
			return Page(items, hasNext)
		}

		private const val ENDPOINT = "https://graphql.anilist.co"
		private val JSON = "application/json; charset=utf-8".toMediaType()

		private const val QUERY = """
query (${'$'}page: Int, ${'$'}type: MediaType, ${'$'}sort: [MediaSort], ${'$'}tags: [String], ${'$'}format: MediaFormat, ${'$'}isAdult: Boolean) {
  Page(page: ${'$'}page, perPage: 30) {
    pageInfo { hasNextPage }
    media(type: ${'$'}type, sort: ${'$'}sort, tag_in: ${'$'}tags, format: ${'$'}format, isAdult: ${'$'}isAdult) {
      id
      title { english romaji }
      coverImage { large }
      averageScore
      isAdult
      genres
      tags { name rank isMediaSpoiler }
    }
  }
}"""

		fun buildRequestBody(feed: Feed, page: Int): JSONObject {
			val variables = JSONObject()
				.put("page", page)
				.put("type", feed.type)
				.put("sort", JSONArray().put(feed.sort))
			feed.tag?.let { variables.put("tags", JSONArray().put(it)) }
			feed.format?.let { variables.put("format", it) }
			feed.adult?.let { variables.put("isAdult", it) }
			return JSONObject().put("query", QUERY.trim()).put("variables", variables)
		}

		/** Parses an AniList `Page` response; English title first, romaji as the fallback. */
		fun parse(body: String): Page {
			val root = JSONObject(body)
			root.optJSONArray("errors")?.optJSONObject(0)?.optString("message")?.let { message ->
				if (message.isNotEmpty()) throw IOException(message)
			}
			val page = root.optJSONObject("data")?.optJSONObject("Page") ?: return Page(emptyList(), false)
			val media = page.optJSONArray("media") ?: JSONArray()
			val items = (0 until media.length()).mapNotNull { i ->
				val entry = media.optJSONObject(i) ?: return@mapNotNull null
				val titles = entry.optJSONObject("title")
				val english = titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
				val romaji = titles?.optString("romaji")?.takeIf { it.isNotBlank() && it != "null" }
				val title = english ?: romaji ?: return@mapNotNull null
				Item(
					id = entry.optInt("id"),
					title = title,
					alternativeTitle = romaji?.takeIf { it != title },
					coverUrl = entry.optJSONObject("coverImage")?.optString("large")?.takeIf { it.startsWith("https://") },
					score = entry.optInt("averageScore", 0).takeIf { it > 0 },
					isAdult = entry.optBoolean("isAdult", false),
					genres = (entry.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) } }
						.orEmpty() + relevantTags(entry.optJSONArray("tags")))
						.filter { it.isNotBlank() && it != "null" }.distinct(),
				)
			}
			val hasNext = page.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: false
			return Page(items, hasNext)
		}

		/** Strong, non-spoiler AniList tags ("Boys' Love", "Isekai") so taste matching sees more than the 19 genres. */
		private fun relevantTags(tags: JSONArray?): List<String> {
			if (tags == null) return emptyList()
			return (0 until tags.length()).mapNotNull { tags.optJSONObject(it) }
				.filter { !it.optBoolean("isMediaSpoiler", false) && it.optInt("rank", 0) >= MIN_TAG_RANK }
				.sortedByDescending { it.optInt("rank", 0) }
				.take(MAX_TAGS)
				.map { it.optString("name") }
		}
	}
}
