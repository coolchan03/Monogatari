package org.koitharu.kotatsu.builtin.audiobookshelf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.sourcerepo.domain.readBoundedString
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Audiobookshelf's documented REST API (https://api.audiobookshelf.org): URL building, response parsing and the
 * connection test. Pure where possible so it can be unit-tested with synthetic JSON.
 */
object AudiobookshelfApi {

	const val PAGE_SIZE = 30
	private const val MAX_BODY_BYTES = 8L * 1024 * 1024
	private const val TIMEOUT_SECONDS = 30L

	/** Library item ids look like "li_8gj2n1b9w6u3v5x7z0"; inos are numeric strings. Both are checked before use in a URL. */
	val ID = Regex("[A-Za-z0-9_-]{1,64}")

	data class Library(val id: String, val name: String, val isPodcast: Boolean)

	data class Item(
		val id: String,
		val title: String,
		val author: String?,
		val narrator: String?,
		val description: String?,
		val genres: List<String>,
	)

	data class Track(val ino: String, val title: String, val number: Float, val publishedAt: Long)

	data class Details(
		val item: Item,
		val authors: List<String>,
		val tracks: List<Track>,
		val isPodcast: Boolean,
	)

	fun pingUrl(server: String) = "$server/ping"

	fun librariesUrl(server: String) = "$server/api/libraries"

	fun itemsUrl(server: String, libraryId: String, page: Int) =
		"$server/api/libraries/$libraryId/items?limit=$PAGE_SIZE&page=${page.coerceAtLeast(0)}&sort=addedAt&desc=1&minified=1"

	fun searchUrl(server: String, libraryId: String, term: String) =
		"$server/api/libraries/$libraryId/search?limit=$PAGE_SIZE&q=${ArchiveMapping.encode(term)}"

	fun itemUrl(server: String, itemId: String) = "$server/api/items/$itemId?expanded=1"

	fun fileUrl(server: String, itemId: String, ino: String) = "$server/api/items/$itemId/file/$ino"

	fun authHeaders(token: String): Map<String, String> = mapOf("Authorization" to "Bearer $token")

	/** Only audiobook ("book") and podcast libraries are supported; anything else is ignored. */
	fun parseLibraries(body: JSONObject): List<Library> {
		val array = body.optJSONArray("libraries") ?: return emptyList()
		return array.objects().mapNotNull { lib ->
			val id = lib.text("id").takeIf { ID.matches(it) } ?: return@mapNotNull null
			val type = lib.text("mediaType")
			if (type != "book" && type != "podcast") return@mapNotNull null
			Library(id, lib.text("name").ifBlank { id }, type == "podcast")
		}
	}

	/** Items of a library page (the "results" array of a minified listing). */
	fun parseItems(body: JSONObject): List<Item> =
		body.optJSONArray("results")?.objects().orEmpty().mapNotNull(::parseItem)

	/** Search hits: both the "book" and "podcast" groups carry a "libraryItem". */
	fun parseSearch(body: JSONObject): List<Item> {
		val hits = ArrayList<Item>()
		for (group in listOf("book", "podcast")) {
			body.optJSONArray(group)?.objects()?.forEach { hit ->
				hit.optJSONObject("libraryItem")?.let(::parseItem)?.let(hits::add)
			}
		}
		return hits.distinctBy { it.id }
	}

	fun parseItem(item: JSONObject): Item? {
		val id = item.text("id").takeIf { ID.matches(it) } ?: return null
		val meta = item.optJSONObject("media")?.optJSONObject("metadata") ?: return null
		val title = meta.text("title").takeIf { it.isNotBlank() } ?: return null
		return Item(
			id = id,
			title = title,
			author = (meta.text("authorName").ifBlank { meta.text("author") }).takeIf { it.isNotBlank() },
			narrator = (meta.text("narratorName")).takeIf { it.isNotBlank() },
			description = meta.text("description").takeIf { it.isNotBlank() },
			genres = meta.optJSONArray("genres").strings(),
		)
	}

	/** An expanded item (GET /api/items/{id}?expanded=1): audio files of a book, or the episodes of a podcast. */
	fun parseDetails(body: JSONObject): Details? {
		val base = parseItem(body) ?: return null
		val media = body.optJSONObject("media") ?: return null
		val meta = media.optJSONObject("metadata")
		val authors = meta?.optJSONArray("authors")?.objects()?.map { it.text("name") }
			?.filter { it.isNotBlank() }.orEmpty()
		val narrators = meta?.optJSONArray("narrators").strings()
		val item = base.copy(narrator = base.narrator ?: narrators.joinToString().takeIf { it.isNotBlank() })
		val episodes = media.optJSONArray("episodes")
		if (body.text("mediaType") == "podcast" || episodes != null) {
			return Details(item, listOfNotNull(item.author), parseEpisodes(episodes), true)
		}
		return Details(item, authors.ifEmpty { listOfNotNull(item.author) }, parseAudioFiles(media.optJSONArray("audioFiles"), item.title), false)
	}

	private fun parseAudioFiles(files: JSONArray?, bookTitle: String): List<Track> {
		val usable = (files ?: return emptyList()).objects()
			.filter { !it.optBoolean("exclude", false) && !it.optBoolean("invalid", false) }
			.mapNotNull { file -> file.text("ino").takeIf { ID.matches(it) }?.let { it to file } }
			.sortedBy { it.second.optInt("index", 0) }
		return usable.mapIndexed { i, (ino, file) ->
			val tagTitle = file.optJSONObject("metaTags")?.text("tagTitle").orEmpty()
			val filename = file.optJSONObject("metadata")?.text("filename").orEmpty().substringBeforeLast('.')
			val title = when {
				usable.size == 1 -> bookTitle
				tagTitle.isNotBlank() -> tagTitle
				filename.isNotBlank() -> filename
				else -> "Part ${i + 1}"
			}
			Track(ino, title, (i + 1).toFloat(), 0L)
		}
	}

	/** Podcast episodes, oldest first so chapter numbers grow with time. */
	private fun parseEpisodes(episodes: JSONArray?): List<Track> {
		val parsed = (episodes ?: return emptyList()).objects().mapNotNull { ep ->
			val ino = ep.optJSONObject("audioFile")?.text("ino")?.takeIf { ID.matches(it) } ?: return@mapNotNull null
			Triple(ino, ep.text("title"), ep.optLong("publishedAt", 0L))
		}.sortedBy { it.third }
		return parsed.mapIndexed { i, (ino, title, publishedAt) ->
			Track(ino, title.ifBlank { "Episode ${i + 1}" }, (i + 1).toFloat(), publishedAt)
		}
	}

	/** User-facing explanation for a failed response, or null when it succeeded. */
	fun httpErrorMessage(code: Int): String? = when {
		code in 200..299 -> null
		code == 401 -> "Audiobookshelf rejected the token. Copy a fresh API token from your user settings in the web app."
		code == 403 -> "Audiobookshelf refused access (HTTP 403). This account may not be allowed to use that library."
		code == 404 -> "Audiobookshelf couldn't find that (HTTP 404). It may have been removed or the address may be wrong."
		code >= 500 -> "Your Audiobookshelf server reported an error (HTTP $code). Try again in a moment."
		else -> "Audiobookshelf request failed (HTTP $code)."
	}

	fun unreachableMessage(host: String) =
		"Can't reach your Audiobookshelf server at $host. Check that it is running; a local address (192.168.x.x) " +
			"only works on the same Wi-Fi network."

	const val NOT_ABS_MESSAGE = "The address answered, but not like an Audiobookshelf server. Check the address and port."

	/** A client with a hard per-call time limit; the app's shared client is otherwise untouched. */
	fun withTimeout(client: OkHttpClient): OkHttpClient =
		client.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

	/** One authenticated GET returning parsed JSON, with a bounded read and plain-language errors. */
	suspend fun get(client: OkHttpClient, url: String, token: String?): JSONObject = withContext(Dispatchers.IO) {
		val builder = Request.Builder().url(url).header("Accept", "application/json")
		if (token != null) builder.header("Authorization", "Bearer $token")
		val request = builder.build()
		val response = try {
			client.newCall(request).execute()
		} catch (e: IOException) {
			throw IOException(unreachableMessage(request.url.host), e)
		}
		response.use {
			httpErrorMessage(it.code)?.let { message -> throw IOException(message) }
			val text = it.body?.readBoundedString(MAX_BODY_BYTES).orEmpty().ifEmpty { "{}" }
			try {
				JSONObject(text)
			} catch (e: JSONException) {
				throw IOException(NOT_ABS_MESSAGE, e)
			}
		}
	}

	/** "Test connection": checks the server answers /ping and that the token can list libraries. */
	suspend fun testConnection(client: OkHttpClient, server: String, token: String): String {
		val http = withTimeout(client)
		val ping = get(http, pingUrl(server), null)
		if (!ping.optBoolean("success", false)) throw IOException(NOT_ABS_MESSAGE)
		val libraries = parseLibraries(get(http, librariesUrl(server), token))
		val books = libraries.count { !it.isPodcast }
		val podcasts = libraries.size - books
		return if (libraries.isEmpty()) {
			"Connected, and the token works, but no audiobook or podcast libraries were found."
		} else {
			"Connected. The token works: $books audiobook and $podcasts podcast ${if (libraries.size == 1) "library" else "libraries"} found."
		}
	}

	/** Android's optString turns a JSON null into the text "null"; this gives an empty string instead. */
	private fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name)

	private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

	private fun JSONArray?.strings(): List<String> =
		if (this == null) emptyList() else (0 until length()).filterNot { isNull(it) }.map { optString(it) }.filter { it.isNotBlank() }
}
