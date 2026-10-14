package org.koitharu.kotatsu.builtin.directstream

import java.net.URI

/** Pure parsing of pasted media links: HLS, DASH, M3U playlists and plain files, with codec diagnostics. */
internal object StreamLinks {

	enum class Kind { HLS, DASH, PLAYLIST_FILE, FILE }

	data class Entry(val title: String, val url: String, val logo: String? = null)

	data class Variant(val url: String, val bandwidth: Long, val width: Int, val height: Int, val codecs: String?, val label: String)

	/** What a link probably is, judged from its path alone (query strings and fragments are ignored). */
	fun kindFromUrl(url: String): Kind {
		val path = url.substringBefore('#').substringBefore('?').lowercase()
		return when {
			path.endsWith(".m3u8") -> Kind.HLS
			path.endsWith(".m3u") -> Kind.PLAYLIST_FILE
			path.endsWith(".mpd") -> Kind.DASH
			else -> Kind.FILE
		}
	}

	fun isHttpUrl(text: String): Boolean {
		val t = text.trim()
		if (!(t.startsWith("http://", true) || t.startsWith("https://", true))) return false
		return runCatching { URI(t).host?.isNotEmpty() == true }.getOrDefault(false)
	}

	fun mimeFor(url: String): String? {
		val path = url.substringBefore('#').substringBefore('?').lowercase()
		return when {
			path.endsWith(".m3u8") -> "application/x-mpegURL"
			path.endsWith(".mpd") -> "application/dash+xml"
			path.endsWith(".mp4") || path.endsWith(".m4v") -> "video/mp4"
			path.endsWith(".webm") -> "video/webm"
			path.endsWith(".mkv") -> "video/x-matroska"
			path.endsWith(".mp3") -> "audio/mpeg"
			path.endsWith(".m4a") || path.endsWith(".aac") -> "audio/mp4"
			path.endsWith(".ogg") || path.endsWith(".opus") -> "audio/ogg"
			else -> null
		}
	}

	fun isMasterPlaylist(text: String) = "#EXT-X-STREAM-INF" in text

	/** An IPTV-style list (channels or videos), as opposed to a single HLS stream made of media segments. */
	fun isEntryList(text: String): Boolean =
		text.trimStart().startsWith("#EXTM3U") && "#EXTINF" in text &&
			"#EXT-X-TARGETDURATION" !in text && "#EXT-X-STREAM-INF" !in text

	/** Entries of an extended M3U list: `#EXTINF:-1 tvg-logo="..." ,Title` followed by the URL line. */
	fun parseEntries(text: String, base: String? = null, limit: Int = 500): List<Entry> {
		val entries = ArrayList<Entry>()
		var title: String? = null
		var logo: String? = null
		for (raw in text.lineSequence()) {
			val line = raw.trim()
			when {
				line.startsWith("#EXTINF") -> {
					title = line.substringAfter(',', "").trim().takeIf { it.isNotEmpty() }
					logo = LOGO.find(line)?.groupValues?.get(1)?.takeIf { it.startsWith("http") }
				}
				line.isEmpty() || line.startsWith("#") -> Unit
				else -> {
					val url = resolve(base, line)
					if (url != null && isHttpUrl(url)) {
						entries += Entry(title ?: titleFromUrl(url), url, logo)
						if (entries.size >= limit) return entries
					}
					title = null
					logo = null
				}
			}
		}
		return entries
	}

	/** Quality variants of an HLS master playlist, best first, each labelled with its resolution, codecs and bitrate. */
	fun parseHlsVariants(text: String, base: String): List<Variant> {
		val variants = ArrayList<Variant>()
		val lines = text.lines().map { it.trim() }
		for ((i, line) in lines.withIndex()) {
			if (!line.startsWith("#EXT-X-STREAM-INF")) continue
			val attrs = attributes(line.substringAfter(':'))
			val uri = lines.drop(i + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
			val url = resolve(base, uri) ?: continue
			val bandwidth = attrs["BANDWIDTH"]?.toLongOrNull() ?: attrs["AVERAGE-BANDWIDTH"]?.toLongOrNull() ?: 0L
			val (w, h) = attrs["RESOLUTION"]?.split('x')?.let { (it.getOrNull(0)?.toIntOrNull() ?: 0) to (it.getOrNull(1)?.toIntOrNull() ?: 0) } ?: (0 to 0)
			val codecs = attrs["CODECS"]
			variants += Variant(url, bandwidth, w, h, codecs, describe(h, bandwidth, codecs))
		}
		return variants.sortedByDescending { it.bandwidth }
	}

	/** "720p · avc1.64001f,mp4a.40.2 · 2.8 Mbps" - what the user sees to judge whether a stream will play. */
	fun describe(height: Int, bandwidth: Long, codecs: String?): String = listOfNotNull(
		height.takeIf { it > 0 }?.let { "${it}p" },
		codecs?.takeIf { it.isNotBlank() },
		bandwidth.takeIf { it > 0 }?.let { bitrate(it) },
	).joinToString(" · ").ifEmpty { "HLS" }

	fun bitrate(bitsPerSecond: Long): String =
		if (bitsPerSecond >= 1_000_000) "%.1f Mbps".format(java.util.Locale.ROOT, bitsPerSecond / 1_000_000.0)
		else "${bitsPerSecond / 1000} kbps"

	/** Video representations of a DASH manifest, best first, as diagnostic labels. */
	fun parseDashLabels(xml: String): List<String> =
		REPRESENTATION.findAll(xml).mapNotNull { match ->
			val tag = match.value
			val attrs = ATTR.findAll(tag).associate { it.groupValues[1] to it.groupValues[2] }
			val mime = attrs["mimeType"].orEmpty()
			val isVideo = mime.startsWith("video") || attrs["height"] != null
			if (!isVideo) return@mapNotNull null
			val height = attrs["height"]?.toIntOrNull() ?: 0
			val bandwidth = attrs["bandwidth"]?.toLongOrNull() ?: 0L
			bandwidth to describe(height, bandwidth, attrs["codecs"]).let { if (it == "HLS") "DASH" else it }
		}.sortedByDescending { it.first }.map { it.second }.distinct().toList()

	private fun attributes(text: String): Map<String, String> =
		ATTRIBUTE.findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim('"') }

	fun resolve(base: String?, ref: String): String? = runCatching {
		if (base == null || ref.startsWith("http://", true) || ref.startsWith("https://", true)) ref
		else URI(base).resolve(ref).toString()
	}.getOrNull()

	private fun titleFromUrl(url: String): String =
		url.substringBefore('#').substringBefore('?').substringAfterLast('/').ifBlank { url }

	private val LOGO = Regex("tvg-logo=\"([^\"]*)\"")
	private val ATTRIBUTE = Regex("([A-Z0-9-]+)=(\"[^\"]*\"|[^,]*)")
	private val REPRESENTATION = Regex("<Representation\\b[^>]*>", RegexOption.IGNORE_CASE)
	private val ATTR = Regex("([A-Za-z]+)=\"([^\"]*)\"")
}
