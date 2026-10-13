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

/** Read-only AniList discovery lists (no account needed). Tapping a result searches the user's own sources. */
class AniListDiscover @Inject constructor(
	@BaseHttpClient private val client: OkHttpClient,
) {

	enum class Feed(
		@StringRes val titleRes: Int,
		val type: String,
		val sort: String,
		val tag: String? = null,
		val format: String? = null,
	) {
		TRENDING_MANGA(R.string.discover_trending_manga, "MANGA", "TRENDING_DESC"),
		TRENDING_BL(R.string.discover_trending_bl, "MANGA", "TRENDING_DESC", tag = "Boys' Love"),
		POPULAR_BL(R.string.discover_popular_bl, "MANGA", "POPULARITY_DESC", tag = "Boys' Love"),
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

	companion object {

		private const val ENDPOINT = "https://graphql.anilist.co"
		private val JSON = "application/json; charset=utf-8".toMediaType()

		private const val QUERY = """
query (${'$'}page: Int, ${'$'}type: MediaType, ${'$'}sort: [MediaSort], ${'$'}tags: [String], ${'$'}format: MediaFormat) {
  Page(page: ${'$'}page, perPage: 30) {
    pageInfo { hasNextPage }
    media(type: ${'$'}type, sort: ${'$'}sort, tag_in: ${'$'}tags, format: ${'$'}format, isAdult: false) {
      id
      title { english romaji }
      coverImage { large }
      averageScore
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
				)
			}
			val hasNext = page.optJSONObject("pageInfo")?.optBoolean("hasNextPage", false) ?: false
			return Page(items, hasNext)
		}
	}
}
