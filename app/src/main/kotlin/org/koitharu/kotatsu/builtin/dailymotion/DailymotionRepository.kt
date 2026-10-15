package org.koitharu.kotatsu.builtin.dailymotion

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
import org.koitharu.kotatsu.parsers.model.ContentRating
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
 * Dailymotion public videos through its documented public Data API (no account or key needed) and the
 * player metadata endpoint for the HLS stream. Each video is one title with a single episode. Videos that
 * Dailymotion restricts (region, age, private, paid) report their reason instead of playing.
 */
class DailymotionRepository(cache: MemoryContentCache, private val client: OkHttpClient) :
	CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.DAILYMOTION
	override val sortOrders = setOf(SortOrder.RELEVANCE, SortOrder.POPULARITY, SortOrder.NEWEST)
	override var defaultSortOrder = SortOrder.POPULARITY
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val term = filter?.query?.trim().orEmpty()
		val body = fetch(listUrl(term, order ?: defaultSortOrder, page = offset / PAGE_SIZE + 1))
		return parseList(body).map { video -> video.toManga() }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val id = manga.url.substringAfterLast('/')
		return manga.copy(
			chapters = listOf(
				MangaChapter(
					id = ArchiveMapping.uid(source, "/episode/$id"),
					title = manga.title,
					number = 1f,
					volume = 0,
					url = id,
					scanlator = null,
					uploadDate = 0L,
					branch = null,
					source = source,
				),
			),
		)
	}

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		val id = chapter.url.substringAfterLast('/')
		require(ID.matches(id)) { "Invalid Dailymotion video id" }
		return parseStreams(fetch("https://www.dailymotion.com/player/metadata/video/$id"))
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun Video.toManga() = Manga(
		id = ArchiveMapping.uid(source, "/video/$id"),
		title = title,
		altTitles = emptySet(),
		url = "/video/$id",
		publicUrl = "https://www.dailymotion.com/video/$id",
		rating = RATING_UNKNOWN,
		contentRating = if (isAdult) ContentRating.ADULT else null,
		coverUrl = thumbnail,
		tags = setOfNotNull(channel?.let { MangaTag(it, it, source) }),
		state = null,
		authors = setOfNotNull(owner),
		largeCoverUrl = null,
		description = description,
		chapters = null,
		source = source,
	)

	private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
		client.newCall(Request.Builder().url(url).build()).execute().use { response ->
			val text = response.body?.string().orEmpty()
			if (!response.isSuccessful && text.isEmpty()) throw IOException("Dailymotion HTTP ${response.code}")
			JSONObject(text.ifEmpty { "{}" })
		}
	}

	data class Video(
		val id: String,
		val title: String,
		val thumbnail: String?,
		val owner: String?,
		val channel: String?,
		val description: String?,
		val isAdult: Boolean,
	)

	companion object {

		const val PAGE_SIZE = 30
		private val ID = Regex("[a-zA-Z0-9]{5,12}")
		private const val FIELDS = "id,title,thumbnail_480_url,owner.screenname,channel.name,description,explicit,private"

		fun listUrl(term: String, order: SortOrder, page: Int): String {
			val sort = when {
				term.isNotEmpty() && order == SortOrder.RELEVANCE -> "relevance"
				order == SortOrder.NEWEST -> "recent"
				term.isEmpty() -> "trending"
				else -> "visited"
			}
			val search = if (term.isEmpty()) "" else "&search=${ArchiveMapping.encode(term)}"
			return "https://api.dailymotion.com/videos?fields=$FIELDS&limit=$PAGE_SIZE&page=$page&sort=$sort$search"
		}

		fun parseList(body: JSONObject): List<Video> {
			body.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
			val list = body.optJSONArray("list") ?: return emptyList()
			return (0 until list.length()).mapNotNull { i ->
				val item = list.optJSONObject(i) ?: return@mapNotNull null
				if (item.optBoolean("private", false)) return@mapNotNull null
				val id = item.optString("id").takeIf { ID.matches(it) } ?: return@mapNotNull null
				Video(
					id = id,
					title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null,
					thumbnail = item.optString("thumbnail_480_url").takeIf { it.startsWith("https://") },
					owner = item.optString("owner.screenname").takeIf { it.isNotBlank() },
					channel = item.optString("channel.name").takeIf { it.isNotBlank() },
					description = item.optString("description").takeIf { it.isNotBlank() },
					isAdult = item.optBoolean("explicit", false),
				)
			}
		}

		/** The player metadata lists HLS manifests per quality; "auto" is the adaptive master playlist. */
		fun parseStreams(metadata: JSONObject): List<MediaStream> {
			metadata.optJSONObject("error")?.let { error ->
				val message = error.optString("title").ifBlank { error.optString("message") }
				throw IOException(message.ifBlank { "This Dailymotion video can't be played" })
			}
			val qualities = metadata.optJSONObject("qualities") ?: return emptyList()
			val streams = ArrayList<MediaStream>()
			val keys = qualities.keys().asSequence().toList()
				.sortedWith(compareBy({ it != "auto" }, { -(it.toIntOrNull() ?: 0) }))
			for (key in keys) {
				val variants = qualities.optJSONArray(key) ?: continue
				for (i in 0 until variants.length()) {
					val variant = variants.optJSONObject(i) ?: continue
					val url = variant.optString("url").takeIf { it.startsWith("https://") } ?: continue
					val type = variant.optString("type")
					streams += MediaStream(
						url = url,
						quality = if (key == "auto") "Auto" else "${key}p",
						mimeType = type.takeIf { it.isNotBlank() },
					)
				}
			}
			return streams.distinctBy { it.url }
		}
	}
}
