package org.koitharu.kotatsu.builtin.vimeo

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
import java.io.IOException

/**
 * Vimeo's public videos through its official API with the user's own access token (More > API keys & accounts).
 * Vimeo does not give other apps a stream URL for public videos, so playback is linked out to vimeo.com.
 */
class VimeoRepository(
	cache: MemoryContentCache,
	private val client: OkHttpClient,
	private val keys: ApiKeys,
) : CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.VIMEO
	override val sortOrders = setOf(SortOrder.RELEVANCE, SortOrder.POPULARITY, SortOrder.NEWEST)
	override var defaultSortOrder = SortOrder.RELEVANCE
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val token = keys.get(ApiServices.VIMEO, "access_token") ?: return emptyList()
		val term = filter?.query?.trim().orEmpty()
		if (term.isEmpty()) return emptyList()
		val page = offset / PAGE_SIZE + 1
		return parseVideos(fetch(listUrl(term, order ?: defaultSortOrder, page), token)).map { it.toManga() }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga = manga.copy(
		chapters = listOf(
			MangaChapter(
				id = ArchiveMapping.uid(source, "/episode${manga.url}"),
				title = manga.title,
				number = 1f,
				volume = 0,
				url = manga.publicUrl,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			),
		),
	)

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
		throw IOException("Vimeo only allows playback on vimeo.com. Open ${chapter.url} in your browser (Open in browser on the title page).")
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun Video.toManga() = Manga(
		id = ArchiveMapping.uid(source, "/video/$id"),
		title = name,
		altTitles = emptySet(),
		url = "/videos/$id",
		publicUrl = link,
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = thumbnail,
		tags = emptySet(),
		state = null,
		authors = setOfNotNull(owner),
		largeCoverUrl = null,
		description = description,
		chapters = null,
		source = source,
	)

	private suspend fun fetch(url: String, token: String): JSONObject = withContext(Dispatchers.IO) {
		val request = Request.Builder().url(url).header("Authorization", "bearer $token")
			.header("Accept", "application/vnd.vimeo.*+json;version=3.4").build()
		client.newCall(request).execute().use { response ->
			when {
				response.code == 401 -> throw IOException("Vimeo rejected the access token. Check it in More > API keys & accounts.")
				!response.isSuccessful -> throw IOException("Vimeo HTTP ${response.code}")
			}
			JSONObject(response.body?.string().orEmpty().ifEmpty { "{}" })
		}
	}

	data class Video(val id: String, val name: String, val link: String, val thumbnail: String?, val owner: String?, val description: String?)

	companion object {

		const val PAGE_SIZE = 20
		private val ID = Regex("\\d{1,15}")

		fun listUrl(term: String, order: SortOrder, page: Int): String {
			val sort = when (order) {
				SortOrder.NEWEST -> "date"
				SortOrder.POPULARITY -> "plays"
				else -> "relevant"
			}
			return "https://api.vimeo.com/videos?per_page=$PAGE_SIZE&page=$page&sort=$sort&query=${ArchiveMapping.encode(term)}"
		}

		fun parseVideos(body: JSONObject): List<Video> {
			val data = body.optJSONArray("data") ?: return emptyList()
			return (0 until data.length()).mapNotNull { i ->
				val v = data.optJSONObject(i) ?: return@mapNotNull null
				val id = v.optString("uri").substringAfterLast('/').takeIf { ID.matches(it) } ?: return@mapNotNull null
				val link = v.optString("link").takeIf { it.startsWith("https://vimeo.com/") } ?: "https://vimeo.com/$id"
				val sizes = v.optJSONObject("pictures")?.optJSONArray("sizes")
				val thumb = sizes?.let { s -> (0 until s.length()).mapNotNull { s.optJSONObject(it) }
					.minByOrNull { kotlin.math.abs(it.optInt("width") - 295) }?.optString("link") }?.takeIf { it.startsWith("https://") }
				Video(
					id, v.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null, link, thumb,
					v.optJSONObject("user")?.optString("name")?.takeIf { it.isNotBlank() },
					v.optString("description").takeIf { it.isNotBlank() },
				)
			}
		}
	}
}
