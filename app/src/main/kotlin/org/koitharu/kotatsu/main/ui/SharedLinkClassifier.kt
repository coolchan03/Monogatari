package org.koitharu.kotatsu.main.ui

import java.net.URI
import java.net.URLDecoder

/**
 * Pure helpers for "Share to Monogatari": find the first link in shared text and decide how to route it.
 * No Android classes on purpose, so it can be unit tested on the JVM.
 */
internal object SharedLinkClassifier {

	sealed interface Result {

		/** Nothing usable was shared. */
		data object Empty : Result

		/** A `monogatari://setup...` link; must go through the existing consent dialog. */
		data class RepoSetup(val link: String) : Result

		/** An https `.json` address, treated as a source repository index; must go through the consent dialog. */
		data class RepoIndex(val url: String) : Result

		/** Text for the YouTube built-in source search (a link, or a `channel:` / `playlist:` query). */
		data class YouTube(val query: String) : Result

		/** An http(s) link that may open as a title. [text] is the whole shared text, used as a fallback search. */
		data class WebLink(val url: String, val text: String) : Result

		/** Plain text, or a link that cannot be handled more specifically; goes to the main search. */
		data class Text(val text: String) : Result
	}

	private const val MAX_TEXT = 1000
	private const val MAX_LINK = 2048
	private const val SETUP_PREFIX = "monogatari://setup"

	private val LINK_REGEX = Regex("""(?i)(?:https?|monogatari)://[^\s<>"']+""")
	private val TRAILING = charArrayOf('.', ',', ';', ':', '!', '?', ')', ']', '}')
	private val YOUTUBE_HOSTS = setOf("youtube.com", "m.youtube.com", "music.youtube.com", "youtu.be")

	/** Returns the first http(s) URL (or `monogatari://setup` link) in [text], without trailing punctuation. */
	fun extractLink(text: String?): String? {
		if (text.isNullOrBlank()) return null
		for (match in LINK_REGEX.findAll(text)) {
			val link = match.value.trimEnd(*TRAILING)
			if (link.length > MAX_LINK) continue
			if (link.startsWith("monogatari://", ignoreCase = true) && !link.startsWith(SETUP_PREFIX, ignoreCase = true)) {
				continue
			}
			return link
		}
		return null
	}

	fun classify(raw: String?): Result {
		val text = raw?.trim().orEmpty().take(MAX_TEXT)
		if (text.isEmpty()) return Result.Empty
		val link = extractLink(text) ?: return Result.Text(text)
		if (link.startsWith("monogatari://", ignoreCase = true)) {
			return Result.RepoSetup(link)
		}
		val uri = try {
			URI(link)
		} catch (_: Exception) {
			return Result.Text(text)
		}
		val host = uri.host?.lowercase()
		if (host.isNullOrEmpty() || uri.userInfo != null) return Result.Text(text)
		if (host in YOUTUBE_HOSTS || host.endsWith(".youtube.com")) {
			return youTubeQuery(uri, host, link)?.let { Result.YouTube(it) } ?: Result.Text(text)
		}
		// A comma would be split into several repositories by the setup link, so such an address stays a plain link.
		if (uri.scheme.equals("https", ignoreCase = true) && ',' !in link &&
			uri.path.orEmpty().endsWith(".json", ignoreCase = true)
		) {
			return Result.RepoIndex(link)
		}
		return Result.WebLink(link, text)
	}

	/** Builds the query for the YouTube source, or null when the link is not a video, channel or playlist. */
	private fun youTubeQuery(uri: URI, host: String, link: String): String? {
		val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
		val params = queryParams(uri.rawQuery)
		if (host == "youtu.be") {
			return if (segments.size == 1) link else null
		}
		val first = segments.firstOrNull() ?: return null
		return when {
			first == "watch" && params["v"] != null -> link
			first == "watch" && params["list"] != null -> "playlist:" + params.getValue("list")
			first == "playlist" -> params["list"]?.let { "playlist:$it" }
			first in setOf("shorts", "live", "embed") && segments.size >= 2 -> link
			first.startsWith("@") && first.length > 1 -> "channel:" + first.substring(1)
			first in setOf("channel", "c", "user") && segments.size >= 2 -> "channel:" + segments[1]
			else -> null
		}
	}

	private fun queryParams(rawQuery: String?): Map<String, String> {
		if (rawQuery.isNullOrEmpty()) return emptyMap()
		val result = HashMap<String, String>()
		for (pair in rawQuery.split('&')) {
			val key = pair.substringBefore('=')
			val value = pair.substringAfter('=', "")
			if (key.isEmpty() || value.isEmpty()) continue
			val decoded = try {
				URLDecoder.decode(value, "UTF-8")
			} catch (_: Exception) {
				continue
			}
			result.putIfAbsent(key, decoded)
		}
		return result
	}
}
