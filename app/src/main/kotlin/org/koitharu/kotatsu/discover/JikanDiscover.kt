package org.koitharu.kotatsu.discover

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions
import java.io.IOException
import kotlin.math.roundToInt

/**
 * Fallback recommendation lists from MyAnimeList through the public Jikan v4 API (no key). Used by [AniListDiscover]
 * when AniList is unreachable, rate limited or rejects the request; results use the same [AniListDiscover.Page] model.
 */
class JikanDiscover(private val client: OkHttpClient) {

	suspend fun load(feed: AniListDiscover.Feed, page: Int): AniListDiscover.Page {
		val url = buildUrl(feed, page)
		val body = execute(url)
		return parse(body)
	}

	private suspend fun execute(url: HttpUrl): String {
		var attempt = 0
		while (true) {
			try {
				return requestSpaced(url)
			} catch (e: TooManyRequestExceptions) {
				// Jikan allows about 3 requests a second; wait briefly once, then let the caller report the error.
				val wait = e.getRetryDelay().coerceAtLeast(1_000L)
				if (attempt++ > 0 || wait > MAX_RETRY_WAIT_MS) throw e
				delay(wait)
			}
		}
	}

	private suspend fun requestSpaced(url: HttpUrl): String = mutex.withLock {
		val wait = lastRequestAt + MIN_SPACING_MS - System.currentTimeMillis()
		if (wait > 0) delay(wait)
		try {
			val request = Request.Builder()
				.url(url)
				.header("Accept", "application/json")
				.header("User-Agent", AniListDiscover.USER_AGENT)
				.get()
				.build()
			client.newCall(request).execute().use { response ->
				val text = response.body?.string()
				if (!response.isSuccessful) {
					val message = text?.let { runCatching { JSONObject(it).optString("message") }.getOrNull() }
						?.takeIf { it.isNotBlank() && it != "null" }
					throw IOException("MyAnimeList returned HTTP ${response.code}" + (message?.let { ": $it" } ?: ""))
				}
				text ?: throw IOException("Empty MyAnimeList response")
			}
		} finally {
			lastRequestAt = System.currentTimeMillis()
		}
	}

	companion object {

		const val PROVIDER_LABEL = "MyAnimeList"

		private const val BASE_URL = "https://api.jikan.moe/v4"
		private const val PAGE_SIZE = 25
		private const val MIN_SPACING_MS = 400L
		private const val MAX_RETRY_WAIT_MS = 3_000L

		/** MyAnimeList genre ids. */
		private const val GENRE_BOYS_LOVE = "28"
		private const val GENRE_HENTAI = "12"

		private val ADULT_GENRES = setOf("hentai", "erotica")

		private val mutex = Mutex()

		@Volatile
		private var lastRequestAt = 0L

		/** The Jikan v4 listing that is closest to each AniList [feed]. */
		fun buildUrl(feed: AniListDiscover.Feed, page: Int): HttpUrl {
			val builder = BASE_URL.toHttpUrl().newBuilder()
			when (feed) {
				AniListDiscover.Feed.TRENDING_MANGA -> builder.addPathSegments("top/manga")
					.addQueryParameter("filter", "publishing")

				AniListDiscover.Feed.TRENDING_BL -> builder.addPathSegment("manga")
					.addQueryParameter("genres", GENRE_BOYS_LOVE)
					.addQueryParameter("status", "publishing")
					.addQueryParameter("order_by", "members")
					.addQueryParameter("sort", "desc")

				AniListDiscover.Feed.POPULAR_BL -> builder.addPathSegment("manga")
					.addQueryParameter("genres", GENRE_BOYS_LOVE)
					.addQueryParameter("order_by", "members")
					.addQueryParameter("sort", "desc")

				AniListDiscover.Feed.ADULT_BL -> builder.addPathSegment("manga")
					.addQueryParameter("genres", "$GENRE_BOYS_LOVE,$GENRE_HENTAI")
					.addQueryParameter("order_by", "members")
					.addQueryParameter("sort", "desc")

				AniListDiscover.Feed.TRENDING_ANIME -> builder.addPathSegments("seasons/now")

				AniListDiscover.Feed.BL_ANIME -> builder.addPathSegment("anime")
					.addQueryParameter("genres", GENRE_BOYS_LOVE)
					.addQueryParameter("order_by", "members")
					.addQueryParameter("sort", "desc")

				AniListDiscover.Feed.LIGHT_NOVELS -> builder.addPathSegments("top/manga")
					.addQueryParameter("type", "lightnovel")
			}
			return builder
				.addQueryParameter("page", page.coerceAtLeast(1).toString())
				.addQueryParameter("limit", PAGE_SIZE.toString())
				.build()
		}

		/**
		 * Parses a Jikan v4 list (`data[]` + `pagination`). English title first, the default (romaji) title as the
		 * alternative. Genres, themes and demographics all feed the "For you" ranking.
		 */
		fun parse(body: String): AniListDiscover.Page {
			val root = JSONObject(body)
			val data = root.optJSONArray("data") ?: run {
				val message = root.optString("message").takeIf { it.isNotBlank() && it != "null" }
				if (message != null) throw IOException(message)
				JSONArray()
			}
			val seen = HashSet<Int>()
			val items = (0 until data.length()).mapNotNull { i ->
				val entry = data.optJSONObject(i) ?: return@mapNotNull null
				val malId = entry.optInt("mal_id", 0).takeIf { it > 0 } ?: return@mapNotNull null
				// Jikan pages occasionally repeat an entry; one card per title.
				if (!seen.add(malId)) return@mapNotNull null
				val english = entry.optCleanString("title_english")
				val original = entry.optCleanString("title")
				val title = english ?: original ?: return@mapNotNull null
				val images = entry.optJSONObject("images")
				val cover = images?.optJSONObject("jpg")?.let { it.optCleanString("large_image_url") ?: it.optCleanString("image_url") }
					?: images?.optJSONObject("webp")?.let { it.optCleanString("large_image_url") ?: it.optCleanString("image_url") }
				val genres = listOf("genres", "explicit_genres", "themes", "demographics")
					.flatMap { key -> entry.optJSONArray(key).nameList() }
					.distinct()
				val rating = entry.optCleanString("rating").orEmpty()
				val isAdult = rating.startsWith("Rx") || genres.any { it.lowercase() in ADULT_GENRES }
				val score = entry.optDouble("score", Double.NaN).takeIf { !it.isNaN() && it > 0.0 }
				AniListDiscover.Item(
					// Negative so MyAnimeList ids never collide with AniList ids in one list.
					id = -malId,
					title = title,
					alternativeTitle = original?.takeIf { it != title },
					coverUrl = cover?.takeIf { it.startsWith("https://") },
					score = score?.let { (it * 10).roundToInt() },
					isAdult = isAdult,
					providerLabel = PROVIDER_LABEL,
					genres = genres,
				)
			}
			val hasNext = root.optJSONObject("pagination")?.optBoolean("has_next_page", false) ?: false
			return AniListDiscover.Page(items, hasNext)
		}

		private fun JSONObject.optCleanString(name: String): String? =
			if (isNull(name)) null else optString(name).trim().takeIf { it.isNotEmpty() && it != "null" }

		private fun JSONArray?.nameList(): List<String> {
			if (this == null) return emptyList()
			return (0 until length()).mapNotNull { optJSONObject(it)?.optCleanString("name") }
		}
	}
}
