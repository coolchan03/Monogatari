package org.koitharu.kotatsu.builtin.odysee

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
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
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.IOException

/**
 * Odysee public videos through the LBRY `claim_search` JSON-RPC proxy that the Odysee website itself uses. Only
 * free, public video claims are listed (mature-tagged claims are excluded); paid or locked claims report an error.
 * Each video is one title with a single episode. Streams use Odysee's public CDN.
 */
class OdyseeRepository(cache: MemoryContentCache, private val client: OkHttpClient) :
	CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.ODYSEE
	override val sortOrders = setOf(SortOrder.RELEVANCE, SortOrder.POPULARITY, SortOrder.NEWEST)
	override var defaultSortOrder = SortOrder.POPULARITY
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val term = filter?.query?.trim().orEmpty()
		val body = rpc(searchRequest(term, order ?: defaultSortOrder, page = offset / PAGE_SIZE + 1))
		return parseClaims(body).map { it.toManga() }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga = manga.copy(
		chapters = listOf(
			MangaChapter(
				id = ArchiveMapping.uid(source, "/episode${manga.url}"),
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
		val (name, claimId) = splitUrl(chapter.url) ?: throw IOException("Invalid Odysee video address")
		val body = rpc(resolveRequest("lbry://$name#$claimId"))
		return streamsFor(parseClaims(body, requireSource = false).firstOrNull() ?: throw IOException("This Odysee video was not found"))
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun Claim.toManga() = Manga(
		id = ArchiveMapping.uid(source, "/claim/$name/$claimId"),
		title = title,
		altTitles = emptySet(),
		url = "/$name/$claimId",
		publicUrl = "https://odysee.com/$name:$claimId",
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = thumbnail,
		tags = tags.take(8).map { MangaTag(it, it, source) }.toSet(),
		state = null,
		authors = setOfNotNull(channel),
		largeCoverUrl = null,
		description = description,
		chapters = null,
		source = source,
	)

	private suspend fun rpc(payload: JSONObject): JSONObject = withContext(Dispatchers.IO) {
		val method = payload.optString("method")
		val request = Request.Builder().url("$ENDPOINT?m=$method")
			.header("User-Agent", "Monogatari/0.14")
			.post(payload.toString().toRequestBody("application/json".toMediaType()))
			.build()
		client.newCall(request).execute().use { response ->
			val text = response.body?.string().orEmpty()
			if (!response.isSuccessful && text.isEmpty()) throw IOException("Odysee HTTP ${response.code}")
			JSONObject(text.ifEmpty { "{}" })
		}
	}

	data class Claim(
		val name: String,
		val claimId: String,
		val title: String,
		val thumbnail: String?,
		val channel: String?,
		val description: String?,
		val tags: List<String>,
		val sdHash: String?,
	)

	companion object {

		const val PAGE_SIZE = 20
		private const val ENDPOINT = "https://api.na-backend.odysee.com/api/v1/proxy"
		private val CLAIM_ID = Regex("[0-9a-f]{40}")
		private val NAME = Regex("[^\\s/:#?]{1,200}")
		private val MATURE_TAGS = listOf("porn", "mature", "nsfw", "xxx", "sex", "erotic")

		fun searchRequest(term: String, order: SortOrder, page: Int): JSONObject {
			val params = JSONObject()
				.put("stream_types", JSONArray(listOf("video")))
				.put("claim_type", JSONArray(listOf("stream")))
				.put("page", page)
				.put("page_size", PAGE_SIZE)
				.put("no_totals", true)
				.put("fee_amount", "<=0")
				.put("not_tags", JSONArray(MATURE_TAGS))
			if (term.isNotEmpty()) params.put("text", term)
			val sort = when {
				order == SortOrder.NEWEST -> "release_time"
				term.isNotEmpty() && order == SortOrder.RELEVANCE -> null
				else -> "trending_group"
			}
			if (sort != null) params.put("order_by", JSONArray(listOf(sort)))
			return JSONObject().put("jsonrpc", "2.0").put("method", "claim_search").put("params", params)
		}

		fun resolveRequest(uri: String): JSONObject = JSONObject().put("jsonrpc", "2.0").put("method", "resolve")
			.put("params", JSONObject().put("urls", JSONArray(listOf(uri))))

		fun splitUrl(url: String): Pair<String, String>? {
			val parts = url.trim('/').split('/')
			if (parts.size != 2) return null
			val (name, id) = parts
			return if (NAME.matches(name) && CLAIM_ID.matches(id)) name to id else null
		}

		fun parseClaims(body: JSONObject, requireSource: Boolean = true): List<Claim> {
			body.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
			val result = body.optJSONObject("result") ?: return emptyList()
			val items = result.optJSONArray("items")
			if (items != null) return (0 until items.length()).mapNotNull { claimOf(items.optJSONObject(it), requireSource) }
			// `resolve` answers with an object keyed by the requested URL.
			return result.keys().asSequence().mapNotNull { claimOf(result.optJSONObject(it), requireSource) }.toList()
		}

		private fun claimOf(item: JSONObject?, requireSource: Boolean): Claim? {
			item ?: return null
			val claimId = item.optString("claim_id").takeIf { CLAIM_ID.matches(it) } ?: return null
			val name = item.optString("name").takeIf { NAME.matches(it) } ?: return null
			val value = item.optJSONObject("value") ?: return null
			val sdHash = value.optJSONObject("source")?.optString("sd_hash")?.takeIf { it.length >= 6 }
			if (requireSource && value.optJSONObject("source") == null) return null
			val tags = value.optJSONArray("tags")?.let { array -> (0 until array.length()).map { array.optString(it) } }.orEmpty()
			if (tags.any { it.lowercase() in MATURE_TAGS }) return null
			val channel = item.optJSONObject("signing_channel")
			return Claim(
				name = name,
				claimId = claimId,
				title = value.optString("title").takeIf { it.isNotBlank() } ?: name,
				thumbnail = value.optJSONObject("thumbnail")?.optString("url")?.takeIf { it.startsWith("https://") },
				channel = channel?.optJSONObject("value")?.optString("title")?.takeIf { it.isNotBlank() }
					?: channel?.optString("name")?.takeIf { it.isNotBlank() },
				description = value.optString("description").takeIf { it.isNotBlank() },
				tags = tags.filter { it.isNotBlank() },
				sdHash = sdHash,
			)
		}

		/** The CDN stream when the claim lists its `sd_hash`, plus the website's own download redirect as a fallback. */
		fun streamsFor(claim: Claim): List<MediaStream> {
			val streams = ArrayList<MediaStream>(2)
			claim.sdHash?.let { hash ->
				streams += MediaStream(
					url = "https://player.odycdn.com/api/v3/streams/free/${claim.name}/${claim.claimId}/${hash.take(6)}.mp4",
					quality = "Source",
					mimeType = "video/mp4",
				)
			}
			streams += MediaStream(
				url = "https://odysee.com/\$/download/${claim.name}/${claim.claimId}",
				quality = if (streams.isEmpty()) "Source" else "Source (fallback)",
				mimeType = "video/mp4",
			)
			return streams
		}
	}
}
