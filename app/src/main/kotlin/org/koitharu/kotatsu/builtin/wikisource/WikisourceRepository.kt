package org.koitharu.kotatsu.builtin.wikisource

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
 * English Wikisource: public-domain and freely licensed texts through the documented MediaWiki API. A work with
 * sub-pages (chapters) becomes a book with one chapter per sub-page; a single page becomes a one-chapter book.
 * Text is licensed CC BY-SA, so every title links back to its Wikisource page.
 */
class WikisourceRepository(cache: MemoryContentCache, private val client: OkHttpClient) :
	CachingMangaRepository(cache), ContinuousMediaProvider {

	override val source = BuiltinSource.WIKISOURCE
	override val sortOrders = setOf(SortOrder.RELEVANCE)
	override var defaultSortOrder = SortOrder.RELEVANCE
	override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)

	override suspend fun getFilterOptions() = MangaListFilterOptions()

	override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
		val term = filter?.query?.trim().orEmpty()
		return parseSearch(fetch(searchUrl(term, offset.coerceAtLeast(0)))).map { it.toManga() }
	}

	override suspend fun getDetailsImpl(manga: Manga): Manga {
		val title = titleOf(manga.url)
		val subpages = parseSubpages(fetch(subpagesUrl(title)), title)
		val chapters = (subpages.ifEmpty { listOf(title) }).mapIndexed { index, page ->
			MangaChapter(
				id = ArchiveMapping.uid(source, "/chapter/$page"),
				title = if (page == title) title else page.removePrefix("$title/"),
				number = (index + 1).toFloat(),
				volume = 0,
				url = page,
				scanlator = null,
				uploadDate = 0L,
				branch = null,
				source = source,
			)
		}
		return manga.copy(chapters = chapters)
	}

	override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String =
		parseHtml(fetch(pageUrl(chapter.url)))

	override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()
	override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = listOf(
		MangaPage(id = ArchiveMapping.uid(source, "/page/${chapter.url}"), url = chapter.url, preview = null, source = source),
	)

	override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
	override suspend fun getPageUrl(page: MangaPage) = page.url

	private fun Hit.toManga() = Manga(
		id = ArchiveMapping.uid(source, "/wiki/$title"),
		title = title,
		altTitles = emptySet(),
		url = "/wiki/$title",
		publicUrl = publicUrl(title),
		rating = RATING_UNKNOWN,
		contentRating = null,
		coverUrl = null,
		tags = emptySet(),
		state = null,
		authors = emptySet(),
		largeCoverUrl = null,
		description = snippet,
		chapters = null,
		source = source,
	)

	private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
		val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
		client.newCall(request).execute().use { response ->
			if (!response.isSuccessful) throw IOException("Wikisource HTTP ${response.code}")
			JSONObject(response.body?.string().orEmpty().ifEmpty { "{}" })
		}
	}

	data class Hit(val title: String, val snippet: String?)

	companion object {

		const val PAGE_SIZE = 20
		private const val API = "https://en.wikisource.org/w/api.php"
		private const val USER_AGENT = "Monogatari/0.14 (https://github.com/coolchan03/Monogatari)"
		private val TAGS = Regex("<[^>]*>")

		/** Search results; an empty [term] lists the featured texts so the source is browsable without typing. */
		fun searchUrl(term: String, offset: Int): String {
			val query = if (term.isEmpty()) "incategory:\"Featured texts\"" else term
			return "$API?action=query&list=search&srnamespace=0&srlimit=$PAGE_SIZE&sroffset=$offset" +
				"&srsearch=${ArchiveMapping.encode(query)}&format=json&formatversion=2"
		}

		fun subpagesUrl(title: String): String =
			"$API?action=query&list=allpages&apnamespace=0&aplimit=200&apprefix=${ArchiveMapping.encode("$title/")}" +
				"&format=json&formatversion=2"

		fun pageUrl(title: String): String =
			"$API?action=parse&page=${ArchiveMapping.encode(title)}&prop=text&redirects=1&disableeditsection=1" +
				"&format=json&formatversion=2"

		fun publicUrl(title: String): String =
			"https://en.wikisource.org/wiki/" + ArchiveMapping.encode(title.replace(' ', '_')).replace("%2F", "/")

		fun titleOf(url: String): String = url.removePrefix("/wiki/")

		fun stripTags(snippet: String): String = snippet.replace(TAGS, "")
			.replace("&quot;", "\"").replace("&amp;", "&").replace("&#039;", "'")
			.replace("&lt;", "<").replace("&gt;", ">").trim()

		fun parseSearch(body: JSONObject): List<Hit> {
			body.optJSONObject("error")?.optString("info")?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
			val hits = body.optJSONObject("query")?.optJSONArray("search") ?: return emptyList()
			return (0 until hits.length()).mapNotNull { i ->
				val item = hits.optJSONObject(i) ?: return@mapNotNull null
				val title = item.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
				Hit(title, stripTags(item.optString("snippet")).takeIf { it.isNotBlank() })
			}
		}

		/** Sub-pages of [title] ("Title/Chapter 1"), in natural order so "Chapter 2" precedes "Chapter 10". */
		fun parseSubpages(body: JSONObject, title: String): List<String> {
			val pages = body.optJSONObject("query")?.optJSONArray("allpages") ?: return emptyList()
			return (0 until pages.length())
				.mapNotNull { pages.optJSONObject(it)?.optString("title") }
				.filter { it.startsWith("$title/") }
				.sortedWith(naturalOrder())
		}

		fun parseHtml(body: JSONObject): String {
			body.optJSONObject("error")?.optString("info")?.takeIf { it.isNotBlank() }?.let { throw IOException(it) }
			return body.optJSONObject("parse")?.optString("text").orEmpty()
		}

		private fun naturalOrder(): Comparator<String> = Comparator { a, b ->
			val ca = chunks(a)
			val cb = chunks(b)
			for (i in 0 until minOf(ca.size, cb.size)) {
				val x = ca[i]
				val y = cb[i]
				val nx = x.toLongOrNull()
				val ny = y.toLongOrNull()
				val c = if (nx != null && ny != null) nx.compareTo(ny) else x.compareTo(y, ignoreCase = true)
				if (c != 0) return@Comparator c
			}
			ca.size.compareTo(cb.size)
		}

		private fun chunks(s: String): List<String> = Regex("\\d+|\\D+").findAll(s).map { it.value }.toList()
	}
}
