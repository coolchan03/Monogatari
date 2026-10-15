package org.koitharu.kotatsu.builtin.bilibili

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
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

/** Public Bilibili metadata and official playback links; no DRM, cookies or geo bypass. */
class BilibiliRepository(cache: MemoryContentCache, private val client: OkHttpClient) :
    CachingMangaRepository(cache), ContinuousMediaProvider {

    override val source = BuiltinSource.BILIBILI
    override val sortOrders = setOf(SortOrder.RELEVANCE, SortOrder.POPULARITY, SortOrder.NEWEST)
    override var defaultSortOrder = SortOrder.RELEVANCE
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val query = filter?.query?.trim().orEmpty()
        if (query.isBlank() && offset > 0) return emptyList()
        val page = offset.coerceAtLeast(0) / PAGE_SIZE + 1
        return parseVideos(fetch(searchUrl(query, order ?: defaultSortOrder, page))).map { it.toManga() }
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val id = manga.url.substringAfterLast('/')
        require(BVID.matches(id)) { "Invalid Bilibili video ID" }
        return manga.copy(chapters = listOf(MangaChapter(
            id = ArchiveMapping.uid(source, "/episode/" + id),
            title = manga.title, number = 1f, volume = 0,
            url = "https://www.bilibili.com/video/" + id,
            scanlator = null, uploadDate = 0L, branch = null, source = source,
        )))
    }

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
        throw IOException("Use Open in browser to watch this video on Bilibili's official site. " +
            "Monogatari does not bypass login, DRM or regional playback restrictions.")
    }

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter) = ""
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPageUrl(page: MangaPage) = page.url

    private fun Video.toManga(): Manga = Manga(
        id = ArchiveMapping.uid(source, "/video/" + id),
        title = title, altTitles = emptySet(), url = "/video/" + id,
        publicUrl = "https://www.bilibili.com/video/" + id,
        rating = RATING_UNKNOWN, contentRating = null, coverUrl = thumbnail,
        tags = setOfNotNull(category?.let { MangaTag(it, it, source) }),
        state = null, authors = setOfNotNull(author),
        largeCoverUrl = null, description = description,
        chapters = null, source = source,
    )

    private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.13)")
            .header("Referer", "https://www.bilibili.com/").build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("Bilibili HTTP " + response.code)
            JSONObject(response.body?.string() ?: throw IOException("Empty Bilibili metadata"))
        }
    }

    data class Video(
        val id: String, val title: String, val thumbnail: String?,
        val author: String?, val category: String?, val description: String?,
    )

    companion object {
        const val PAGE_SIZE = 20
        private val BVID = Regex("BV[A-Za-z0-9]{10}")

        fun searchUrl(query: String, order: SortOrder, page: Int): String {
            if (query.isBlank())
                return "https://api.bilibili.com/x/web-interface/ranking/v2?rid=0&type=all"
            val sorting = when (order) {
                SortOrder.NEWEST -> "pubdate"
                SortOrder.POPULARITY -> "click"
                else -> "totalrank"
            }
            return "https://api.bilibili.com/x/web-interface/search/type" +
                "?search_type=video&keyword=" + ArchiveMapping.encode(query.take(120)) +
                "&page=" + page.coerceAtLeast(1) + "&order=" + sorting
        }

        fun parseVideos(payload: JSONObject): List<Video> {
            val code = payload.optInt("code", -1)
            if (code != 0) throw IOException("Bilibili search: " +
                payload.optString("message").take(120).ifBlank { "Error code " + code })
            val data = payload.optJSONObject("data") ?: return emptyList()
            val items = data.optJSONArray("result") ?: data.optJSONArray("list")
                ?: return emptyList()
            return (0 until items.length()).mapNotNull { i ->
                val entry = items.optJSONObject(i) ?: return@mapNotNull null
                if (entry.optInt("is_pay") != 0 || entry.optInt("badgepay") != 0)
                    return@mapNotNull null
                val id = entry.optString("bvid").takeIf { BVID.matches(it) }
                    ?: return@mapNotNull null
                val title = Jsoup.parse(entry.optString("title")).text().trim()
                    .takeIf(String::isNotBlank) ?: return@mapNotNull null
                val pic = entry.optString("pic")
                val thumb = when {
                    pic.startsWith("//") -> "https:" + pic
                    pic.startsWith("https://") -> pic
                    else -> null
                }?.takeIf { it.length < 2048 }
                Video(
                    id, title, thumb,
                    entry.optString("author").takeIf(String::isNotBlank)
                        ?: entry.optJSONObject("owner")?.optString("name")?.takeIf(String::isNotBlank),
                    entry.optString("typename").takeIf(String::isNotBlank)
                        ?: entry.optString("tname").takeIf(String::isNotBlank),
                    Jsoup.parse(entry.optString("description")).text().take(500)
                        .takeIf(String::isNotBlank),
                )
            }.distinctBy { it.id }
        }
    }
}
