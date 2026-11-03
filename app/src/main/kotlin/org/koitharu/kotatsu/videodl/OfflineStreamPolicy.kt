package org.koitharu.kotatsu.videodl

import java.net.URI
import java.util.Locale

/**
 * Pure rules for deciding whether a stream can be saved for offline playback. Monogatari never
 * bypasses DRM and does not record live streams; those are refused with a [Reason].
 */
object OfflineStreamPolicy {

	enum class Protocol { PROGRESSIVE, HLS, DASH }

	enum class Reason { NO_URL, NOT_HTTP, UNSUPPORTED_FORMAT, LIVE, DRM }

	/** [protocol] is set when the stream may be downloaded, [reason] when it may not. */
	data class Verdict(val protocol: Protocol?, val reason: Reason?) {
		val allowed: Boolean get() = protocol != null
	}

	/** What a fetched HLS playlist turned out to be. */
	enum class HlsKind { MASTER, VOD, LIVE, DRM }

	private val STREAMING_TYPES = setOf(
		"application/x-mpegurl", "application/vnd.apple.mpegurl", "audio/mpegurl", "audio/x-mpegurl",
	)
	private const val DASH_TYPE = "application/dash+xml"
	private const val SMOOTH_TYPE = "application/vnd.ms-sstr+xml"

	/** Decides from the URL and mime type alone; manifests are inspected later with [inspectHls] / [inspectDash]. */
	fun classify(url: String?, mimeType: String?): Verdict {
		val value = url?.trim().orEmpty()
		if (value.isEmpty()) return Verdict(null, Reason.NO_URL)
		val scheme = value.substringBefore(':', "").lowercase(Locale.ROOT)
		if (scheme != "http" && scheme != "https") return Verdict(null, Reason.NOT_HTTP)
		val mime = mimeType?.trim()?.lowercase(Locale.ROOT).orEmpty()
		val path = runCatching { URI(value).path }.getOrNull().orEmpty().lowercase(Locale.ROOT)
		return when {
			mime in STREAMING_TYPES || path.endsWith(".m3u8") -> Verdict(Protocol.HLS, null)
			mime == DASH_TYPE || path.endsWith(".mpd") -> Verdict(Protocol.DASH, null)
			mime == SMOOTH_TYPE || path.endsWith(".ism") || path.contains(".ism/") ->
				Verdict(null, Reason.UNSUPPORTED_FORMAT)
			else -> Verdict(Protocol.PROGRESSIVE, null)
		}
	}

	/** Classifies the text of an HLS playlist: a master playlist, a finished one, a live one or a protected one. */
	fun inspectHls(playlist: String): HlsKind {
		val lines = playlist.lineSequence().map { it.trim() }.toList()
		if (lines.any { (it.startsWith("#EXT-X-KEY") || it.startsWith("#EXT-X-SESSION-KEY")) && isProtectedKey(it) }) {
			return HlsKind.DRM
		}
		if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) return HlsKind.MASTER
		val finished = lines.any { it.startsWith("#EXT-X-ENDLIST") } ||
			lines.any { it.startsWith("#EXT-X-PLAYLIST-TYPE") && it.endsWith("VOD") }
		return if (finished) HlsKind.VOD else HlsKind.LIVE
	}

	/** Plain AES-128 with a normal key URI is not DRM; SAMPLE-AES and key systems (Widevine, FairPlay...) are. */
	private fun isProtectedKey(line: String): Boolean {
		val attributes = line.substringAfter(':', "")
		val method = attribute(attributes, "METHOD")?.uppercase(Locale.ROOT)
		if (method == "NONE") return false
		if (method != null && method.startsWith("SAMPLE-AES")) return true
		val format = attribute(attributes, "KEYFORMAT")
		return format != null && !format.equals("identity", ignoreCase = true)
	}

	private fun attribute(attributes: String, name: String): String? {
		val match = Regex("(?:^|,)$name=(\"[^\"]*\"|[^,]*)").find(attributes) ?: return null
		return match.groupValues[1].trim('"')
	}

	/** The URI of the highest-bandwidth variant of a master playlist, or null when there is none. */
	fun bestHlsVariant(master: String): String? {
		val lines = master.lineSequence().map { it.trim() }.toList()
		var best: String? = null
		var bestBandwidth = -1L
		for ((index, line) in lines.withIndex()) {
			if (!line.startsWith("#EXT-X-STREAM-INF")) continue
			val uri = lines.drop(index + 1).firstOrNull { it.isNotEmpty() && !it.startsWith("#") } ?: continue
			val bandwidth = attribute(line.substringAfter(':', ""), "BANDWIDTH")?.toLongOrNull() ?: 0L
			if (bandwidth > bestBandwidth) {
				bestBandwidth = bandwidth
				best = uri
			}
		}
		return best
	}

	/** True when a master playlist keeps audio in separate renditions (those need their own downloads). */
	fun hasSeparateHlsAudio(master: String): Boolean = master.lineSequence().any {
		val line = it.trim()
		line.startsWith("#EXT-X-MEDIA") && line.contains("TYPE=AUDIO") && line.contains("URI=")
	}

	/** One representation to download: period, adaptation set and representation index in the manifest. */
	data class DashKey(val period: Int, val adaptationSet: Int, val representation: Int) {
		fun encode(): String = "$period:$adaptationSet:$representation"

		companion object {
			fun decode(value: String): DashKey? {
				val parts = value.split(':').map { it.toIntOrNull() ?: return null }
				return if (parts.size == 3) DashKey(parts[0], parts[1], parts[2]) else null
			}
		}
	}

	/**
	 * The highest-bandwidth representation of every adaptation set, so one quality is saved instead of
	 * all of them. Empty when the manifest could not be read (then everything would be downloaded).
	 */
	fun dashStreamKeys(manifest: String): List<DashKey> {
		var period = -1
		var set = -1
		var representation = -1
		val best = LinkedHashMap<Pair<Int, Int>, Pair<Int, Long>>()
		for (match in Regex("<(Period|AdaptationSet|Representation)\\b([^>]*)>").findAll(manifest)) {
			when (match.groupValues[1]) {
				"Period" -> {
					period++
					set = -1
				}
				"AdaptationSet" -> {
					set++
					representation = -1
				}
				else -> {
					representation++
					if (period < 0 || set < 0) continue
					val bandwidth = Regex("\\bbandwidth\\s*=\\s*[\"'](\\d+)[\"']").find(match.groupValues[2])
						?.groupValues?.get(1)?.toLongOrNull() ?: 0L
					val current = best[period to set]
					if (current == null || bandwidth > current.second) best[period to set] = representation to bandwidth
				}
			}
		}
		return best.map { (key, value) -> DashKey(key.first, key.second, value.first) }
	}

	/** Null when the DASH manifest is a finished, unencrypted presentation; otherwise why it is refused. */
	fun inspectDash(manifest: String): Reason? {
		if (Regex("<MPD\\b[^>]*\\btype\\s*=\\s*[\"']dynamic[\"']", RegexOption.IGNORE_CASE).containsMatchIn(manifest)) {
			return Reason.LIVE
		}
		if (manifest.contains("<ContentProtection", ignoreCase = true)) return Reason.DRM
		return null
	}

	/** Resolves a (possibly relative) playlist URI against the playlist it was found in. */
	fun resolve(base: String, reference: String): String =
		runCatching { URI(base).resolve(reference).toString() }.getOrDefault(reference)
}
