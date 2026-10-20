package org.koitharu.kotatsu.builtin.komga

import org.json.JSONObject
import java.net.URLEncoder

/**
 * Draft (v2.0) client core for a user's own Komga server (self-hosted comics/manga/ebook library).
 *
 * Pure request building and response parsing, so it can be tested without a server; it is not registered as a source
 * yet. Authentication uses a Komga API key sent as the `X-API-Key` header, never in a URL.
 */
object KomgaApi {

	const val AUTH_HEADER = "X-API-Key"

	data class Series(val id: String, val title: String, val booksCount: Int, val summary: String?, val genres: List<String>)

	data class Book(val id: String, val title: String, val number: Float, val pagesCount: Int, val mediaType: String?)

	data class Page<T>(val items: List<T>, val pageNumber: Int, val isLast: Boolean)

	fun searchSeriesUrl(server: String, query: String, page: Int, size: Int = 30): String =
		"${base(server)}/api/v1/series?search=${encode(query)}&page=$page&size=$size&sort=metadata.titleSort,asc"

	fun recentSeriesUrl(server: String, page: Int, size: Int = 30): String =
		"${base(server)}/api/v1/series/latest?page=$page&size=$size"

	fun booksUrl(server: String, seriesId: String, page: Int, size: Int = 500): String =
		"${base(server)}/api/v1/series/${id(seriesId)}/books?page=$page&size=$size&sort=metadata.numberSort,asc"

	fun pageImageUrl(server: String, bookId: String, pageNumber: Int): String =
		"${base(server)}/api/v1/books/${id(bookId)}/pages/$pageNumber"

	fun thumbnailUrl(server: String, seriesId: String): String =
		"${base(server)}/api/v1/series/${id(seriesId)}/thumbnail"

	fun parseSeries(json: String): Page<Series> = parsePage(json) { item ->
		val metadata = item.optJSONObject("metadata")
		val title = metadata?.optString("title").orEmpty().ifBlank { item.optString("name") }
		if (title.isBlank()) return@parsePage null
		Series(
			id = item.optString("id").takeIf { ID.matches(it) } ?: return@parsePage null,
			title = title,
			booksCount = item.optInt("booksCount", 0),
			summary = metadata?.optString("summary")?.takeIf { it.isNotBlank() },
			genres = metadata?.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.optString(it) } }
				.orEmpty().filter { it.isNotBlank() },
		)
	}

	fun parseBooks(json: String): Page<Book> = parsePage(json) { item ->
		val metadata = item.optJSONObject("metadata")
		Book(
			id = item.optString("id").takeIf { ID.matches(it) } ?: return@parsePage null,
			title = metadata?.optString("title").orEmpty().ifBlank { item.optString("name") },
			number = metadata?.optString("number")?.toFloatOrNull() ?: item.optDouble("number", 0.0).toFloat(),
			pagesCount = item.optJSONObject("media")?.optInt("pagesCount", 0) ?: 0,
			mediaType = item.optJSONObject("media")?.optString("mediaType")?.takeIf { it.isNotBlank() },
		)
	}

	private fun <T> parsePage(json: String, map: (JSONObject) -> T?): Page<T> {
		val root = JSONObject(json)
		val content = root.optJSONArray("content")
		val items = (0 until (content?.length() ?: 0)).mapNotNull { content?.optJSONObject(it)?.let(map) }
		return Page(items, root.optInt("number", 0), root.optBoolean("last", true))
	}

	private val ID = Regex("[A-Za-z0-9]{1,64}")

	private fun base(server: String) = server.trim().trimEnd('/')

	private fun id(value: String): String = value.takeIf { ID.matches(it) } ?: throw IllegalArgumentException("Invalid Komga id")

	private fun encode(value: String) = URLEncoder.encode(value.trim().take(200), "UTF-8")
}
