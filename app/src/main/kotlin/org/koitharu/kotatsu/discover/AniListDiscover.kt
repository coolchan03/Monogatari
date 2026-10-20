package org.koitharu.kotatsu.discover

import androidx.annotation.StringRes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.network.BaseHttpClient
import java.io.IOException
import javax.inject.Inject

/** Read-only AniList discovery lists (no account needed, adult titles included). Tapping a result searches the user's own sources. */
class AniListDiscover @Inject constructor(
	@BaseHttpClient private val client: OkHttpClient,
) {

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
	)

	class Page(val items: List<Item>, val hasNextPage: Boolean)

	suspend fun load(feed: Feed, page: Int): Page = withContext(Dispatchers.IO) {
		val request = Request.Builder()
			.url(ENDPOINT)
			.header("Accept", "application/json")
			.post(buildRequestBody(feed, page).toString().toRequestBody(JSON))
			.build()
		val body = client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) throw IOException("AniList returned HTTP ${response.code}")
			response.body?.string() ?: throw IOException("Empty AniList response")
		}
		parse(body)
	}

	/**
	 * v1.3 draft release calendar: anime episodes airing between now and [days] days ahead, soonest first.
	 * [label] formats the episode number and airing time (epoch seconds) for the card badge.
	 */
	suspend fun loadAiring(page: Int, days: Int, label: (episode: Int, airingAt: Long) -> String): Page =
		withContext(Dispatchers.IO) {
			val now = System.currentTimeMillis() / 1000
			val request = Request.Builder()
				.url(ENDPOINT)
				.header("Accept", "application/json")
				.post(buildAiringRequestBody(page, now, now + days * 86_400L).toString().toRequestBody(JSON))
				.build()
			val body = client.newCall(request).execute().use { response ->
				if (!response.isSuccessful) throw IOException("AniList returned HTTP ${response.code}")
				response.body?.string() ?: throw IOException("Empty AniList response")
			}
			parseAiring(body, label)
		}

	companion object {

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
					title = title,
					alternativeTitle = romaji?.takeIf { it != title },
					coverUrl = media.optJSONObject("coverImage")?.optString("large")?.takeIf { it.startsWith("https://") },
					score = null,
					isAdult = media.optBoolean("isAdult", false),
					providerLabel = label(episode, airingAt),
					genres = media.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) } }
						.orEmpty().filter { it.isNotBlank() },
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
					genres = entry.optJSONArray("genres")?.let { g -> (0 until g.length()).map { g.optString(it) } }
						.orEmpty().filter { it.isNotBlank() },
				)
			}
			val hasNext = page.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: false
			return Page(items, hasNext)
		}
	}
}
