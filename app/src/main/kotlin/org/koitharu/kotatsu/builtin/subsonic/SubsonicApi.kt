package org.koitharu.kotatsu.builtin.subsonic

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
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/**
 * The Subsonic REST API 1.16.1 (also served by Navidrome, Gonic and Airsonic): request signing, URL building and
 * response parsing. The password is never sent: every request carries t = md5(password + salt) with a fresh salt.
 */
object SubsonicApi {

	const val PAGE_SIZE = 30
	const val API_VERSION = "1.16.1"
	const val CLIENT_NAME = "Monogatari"
	private const val MAX_BODY_BYTES = 8L * 1024 * 1024
	private const val TIMEOUT_SECONDS = 30L
	private val random = SecureRandom()

	/** Ids are opaque strings (Navidrome: 22 letters/digits, Gonic: "al-12"); checked before use in a URL. */
	val ID = Regex("[A-Za-z0-9._:-]{1,80}")

	data class Album(val id: String, val name: String, val artist: String?, val year: Int?, val genre: String?)

	data class Song(val id: String, val title: String, val disc: Int, val track: Int, val durationSeconds: Int)

	data class AlbumDetails(val album: Album, val songs: List<Song>)

	fun md5Hex(text: String): String =
		MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

	/** A fresh random salt (16 hex characters) for one request. */
	fun newSalt(): String {
		val bytes = ByteArray(8)
		random.nextBytes(bytes)
		return bytes.joinToString("") { "%02x".format(it) }
	}

	/** The authentication query: u, t, s, v, c, f. The plain password never appears in it. */
	fun authQuery(user: String, password: String, salt: String): String =
		"u=${ArchiveMapping.encode(user)}&t=${md5Hex(password + salt)}&s=$salt&v=$API_VERSION&c=$CLIENT_NAME&f=json"

	/** "$server/rest/$method.view?auth&params"; [params] are encoded here. */
	fun url(
		server: String,
		method: String,
		params: List<Pair<String, String>>,
		user: String,
		password: String,
		salt: String = newSalt(),
	): String {
		val extra = params.joinToString("") { (k, v) -> "&$k=${ArchiveMapping.encode(v)}" }
		return "$server/rest/$method.view?${authQuery(user, password, salt)}$extra"
	}

	fun albumListParams(sortNewest: Boolean, offset: Int) = listOf(
		"type" to if (sortNewest) "newest" else "alphabeticalByName",
		"size" to PAGE_SIZE.toString(),
		"offset" to offset.coerceAtLeast(0).toString(),
	)

	fun searchParams(term: String, offset: Int) = listOf(
		"query" to term,
		"albumCount" to PAGE_SIZE.toString(),
		"albumOffset" to offset.coerceAtLeast(0).toString(),
		"artistCount" to "0",
		"songCount" to "0",
	)

	/** Returns the "subsonic-response" object, or throws an [IOException] explaining a "failed" status. */
	fun checkResponse(body: JSONObject): JSONObject {
		val response = body.optJSONObject("subsonic-response") ?: throw IOException(NOT_SUBSONIC_MESSAGE)
		if (response.text("status") == "ok") return response
		val error = response.optJSONObject("error")
		throw IOException(errorMessage(error?.optInt("code", 0) ?: 0, error?.text("message").orEmpty()))
	}

	fun errorMessage(code: Int, message: String): String = when (code) {
		40 -> "Your server rejected the username or password. Check them in More > API keys & accounts."
		41 -> "Your server doesn't support the secure sign-in this app uses (token authentication). " +
			"Servers linked to LDAP or an external login can't be used."
		50 -> "That user isn't allowed to do this on your server."
		70 -> "That item is no longer on your server."
		else -> "Your server reported an error" + (if (message.isNotBlank()) ": $message" else " (code $code).")
	}

	fun parseAlbumList(response: JSONObject): List<Album> =
		parseAlbums(response.optJSONObject("albumList2")?.optJSONArray("album"))

	fun parseSearch(response: JSONObject): List<Album> =
		parseAlbums(response.optJSONObject("searchResult3")?.optJSONArray("album"))

	fun parseAlbumDetails(response: JSONObject): AlbumDetails? {
		val obj = response.optJSONObject("album") ?: return null
		val album = parseAlbum(obj) ?: return null
		val songs = (obj.optJSONArray("song")?.objects().orEmpty()).mapNotNull { s ->
			val id = s.text("id").takeIf { ID.matches(it) } ?: return@mapNotNull null
			Song(
				id = id,
				title = s.text("title").ifBlank { "Track" },
				disc = s.optInt("discNumber", 0),
				track = s.optInt("track", 0),
				durationSeconds = s.optInt("duration", 0),
			)
		}.sortedWith(compareBy({ it.disc }, { it.track })) // stable: tracks without numbers keep server order
		return AlbumDetails(album, songs)
	}

	private fun parseAlbums(array: JSONArray?): List<Album> = array?.objects().orEmpty().mapNotNull(::parseAlbum)

	private fun parseAlbum(obj: JSONObject): Album? {
		val id = obj.text("id").takeIf { ID.matches(it) } ?: return null
		val name = obj.text("name").ifBlank { obj.text("title") }.takeIf { it.isNotBlank() } ?: return null
		return Album(
			id = id,
			name = name,
			artist = obj.text("artist").takeIf { it.isNotBlank() },
			year = obj.optInt("year", 0).takeIf { it in 1000..2100 },
			genre = obj.text("genre").takeIf { it.isNotBlank() },
		)
	}

	/** "2 · Title (4:05)", or "2-3 · Title" style for a multi-disc album. */
	fun chapterTitle(song: Song, multiDisc: Boolean, index: Int): String {
		val number = when {
			song.track <= 0 -> (index + 1).toString()
			multiDisc && song.disc > 0 -> "${song.disc}-${song.track}"
			else -> song.track.toString()
		}
		val duration = if (song.durationSeconds > 0) " (${formatDuration(song.durationSeconds)})" else ""
		return "$number · ${song.title}$duration"
	}

	fun formatDuration(seconds: Int): String {
		val h = seconds / 3600
		val m = seconds % 3600 / 60
		val s = seconds % 60
		return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
	}

	const val NOT_SUBSONIC_MESSAGE = "The address answered, but not like a Subsonic or Navidrome server. " +
		"Check the address and port (Navidrome is usually 4533)."

	fun httpErrorMessage(code: Int): String? = when {
		code in 200..299 -> null
		code == 401 || code == 403 -> "Your server refused the request (HTTP $code). Check the address and your login."
		code == 404 -> "Nothing answered like a Subsonic server at that address (HTTP 404). Check the address and port."
		code >= 500 -> "Your server reported an error (HTTP $code). Try again in a moment."
		else -> "Request failed (HTTP $code)."
	}

	fun unreachableMessage(host: String) =
		"Can't reach your server at $host. Check that it is running; a local address (192.168.x.x) " +
			"only works on the same Wi-Fi network."

	fun withTimeout(client: OkHttpClient): OkHttpClient =
		client.newBuilder().callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS).build()

	/** One signed GET returning the checked "subsonic-response" object, with a bounded read. */
	suspend fun call(
		client: OkHttpClient,
		server: String,
		method: String,
		params: List<Pair<String, String>>,
		user: String,
		password: String,
	): JSONObject = withContext(Dispatchers.IO) {
		val request = Request.Builder().url(url(server, method, params, user, password)).build()
		val response = try {
			client.newCall(request).execute()
		} catch (e: IOException) {
			throw IOException(unreachableMessage(request.url.host), e)
		}
		response.use {
			httpErrorMessage(it.code)?.let { message -> throw IOException(message) }
			val text = it.body?.readBoundedString(MAX_BODY_BYTES).orEmpty()
			val json = try {
				JSONObject(text)
			} catch (e: JSONException) {
				throw IOException(NOT_SUBSONIC_MESSAGE, e)
			}
			checkResponse(json)
		}
	}

	/** "Test connection": a signed ping, so it checks the address, the API and the login together. */
	suspend fun testConnection(client: OkHttpClient, server: String, user: String, password: String): String {
		val response = call(withTimeout(client), server, "ping", emptyList(), user, password)
		val type = response.text("type").takeIf { it.isNotBlank() }
		val version = response.text("serverVersion").takeIf { it.isNotBlank() }
		val name = listOfNotNull(type, version).joinToString(" ").ifBlank { "Subsonic API ${response.text("version")}" }
		return "Connected. The login works ($name)."
	}

	/** Android's optString turns a JSON null into the text "null"; this gives an empty string instead. */
	private fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name)

	private fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }
}
