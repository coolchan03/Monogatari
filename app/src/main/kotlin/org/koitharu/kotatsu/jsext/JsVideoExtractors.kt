package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okio.ByteString.Companion.decodeBase64
import org.jsoup.Jsoup
import java.net.URI
import kotlin.random.Random

/**
 * Video-host extractors that Mangayomi exposes to JS extensions (`mp4UploadExtractor`, `doodExtractor`, …).
 * They follow the same approach as Mangayomi's and Aniyomi's extractors (Apache-2.0): fetch the embed page,
 * unpack/parse the player script and return direct stream URLs. Video hosts change their pages often, so every
 * extractor returns an empty list instead of throwing when a page no longer matches.
 */
internal class JsVideoExtractors(private val transport: JsHttpTransport) {

	class Video(
		val url: String,
		val quality: String,
		val headers: Map<String, String> = emptyMap(),
		val subtitles: List<Pair<String, String>> = emptyList(),
	)

	suspend fun extract(name: String, args: JsonArray): List<Video>? {
		fun arg(i: Int) = args.getOrNull(i).str().orEmpty()
		fun headersArg(i: Int): Map<String, String> = arg(i).takeIf { it.isNotBlank() }?.let { raw ->
			runCatching { JsHttpBridge.json.parseToJsonElement(raw).stringMap() }.getOrNull()
		}.orEmpty()
		val url = arg(0)
		if (!url.startsWith("http://") && !url.startsWith("https://")) return emptyList()
		return when (name) {
			"mp4UploadExtractor" -> mp4Upload(url, headersArg(1), arg(2), arg(3))
			"streamTapeExtractor" -> streamTape(url, arg(1).ifBlank { "StreamTape" })
			"doodExtractor" -> dood(url, arg(1).ifBlank { "Doodstream" })
			"streamWishExtractor" -> streamWish(url, arg(1))
			"filemoonExtractor" -> filemoon(url, arg(1), arg(2))
			"voeExtractor" -> voe(url, arg(1))
			"okruExtractor" -> okru(url)
			"sibnetExtractor" -> sibnet(url, arg(1))
			"yourUploadExtractor" -> yourUpload(url, headersArg(1), arg(2), arg(3))
			"vidBomExtractor" -> vidBom(url)
			"streamlareExtractor" -> streamlare(url, arg(1), arg(2))
			else -> null
		}
	}

	private suspend fun mp4Upload(url: String, headers: Map<String, String>, prefix: String, suffix: String): List<Video> {
		val referer = mapOf("Referer" to "https://www.mp4upload.com/")
		val page = get(url, headers + referer) ?: return emptyList()
		val script = JsUnpacker.unpackAndCombine(page) ?: page
		val src = Regex("""(?:player\.src\(\s*\{[^}]*?src\s*:|src\s*:)\s*["']([^"']+\.mp4[^"']*)["']""").find(script)
			?.groupValues?.get(1) ?: return emptyList()
		val height = Regex("""HEIGHT\s*=\s*(\d+)|height\s*[:=]\s*["']?(\d+)""").find(script)
			?.groupValues?.drop(1)?.firstOrNull { it.isNotEmpty() }
		val label = buildString {
			append(prefix).append("Mp4Upload")
			if (height != null) append(" - ").append(height).append('p')
			append(suffix)
		}
		return listOf(Video(src, label, referer))
	}

	private suspend fun streamTape(url: String, quality: String): List<Video> {
		val embed = if ("/e/" in url) url else {
			val id = url.substringAfter("/v/", "").substringBefore('/').ifEmpty { return emptyList() }
			"https://streamtape.com/e/$id"
		}
		val page = get(embed) ?: return emptyList()
		val videoUrl = parseStreamTape(page) ?: return emptyList()
		return listOf(Video(videoUrl, quality))
	}

	private suspend fun dood(url: String, quality: String): List<Video> {
		val response = request(url) ?: return emptyList()
		val finalUrl = response.finalUrl.ifBlank { url }
		val host = runCatching { URI(finalUrl).host }.getOrNull() ?: return emptyList()
		val md5Path = Regex("""['"]/pass_md5/([^'"]+)['"]""").find(response.body)?.groupValues?.get(1)
			?: return emptyList()
		val token = md5Path.substringAfterLast('/')
		val referer = mapOf("Referer" to finalUrl)
		val prefix = get("https://$host/pass_md5/$md5Path", referer)?.trim()?.takeIf { it.startsWith("http") }
			?: return emptyList()
		val random = (1..10).map { ALPHANUMERIC[Random.nextInt(ALPHANUMERIC.length)] }.joinToString("")
		val videoUrl = "$prefix$random?token=$token&expiry=${System.currentTimeMillis()}"
		return listOf(Video(videoUrl, quality, mapOf("Referer" to "https://$host/")))
	}

	private suspend fun streamWish(url: String, prefix: String): List<Video> {
		val page = get(url) ?: return emptyList()
		val master = findM3u8(page) ?: return emptyList()
		return hlsVariants(master, mapOf("Referer" to url), "${prefix}StreamWish")
	}

	private suspend fun filemoon(url: String, prefix: String, suffix: String): List<Video> {
		var page = get(url) ?: return emptyList()
		var referer = url
		if (findM3u8(page) == null) {
			// Newer Filemoon pages wrap the player in an iframe on another host.
			val iframe = Jsoup.parse(page, url).selectFirst("iframe[src]")?.attr("abs:src")
			if (!iframe.isNullOrBlank()) {
				page = get(iframe, mapOf("Referer" to url)) ?: return emptyList()
				referer = iframe
			}
		}
		val master = findM3u8(page) ?: return emptyList()
		val origin = URI(referer).let { "${it.scheme}://${it.host}/" }
		return hlsVariants(master, mapOf("Referer" to origin, "Origin" to origin.trimEnd('/')), "${prefix}Filemoon", suffix)
	}

	private suspend fun voe(url: String, quality: String): List<Video> {
		var page = get(url) ?: return emptyList()
		// Voe often answers with a tiny page that redirects through JavaScript.
		Regex("""window\.location\.href\s*=\s*['"]([^'"]+)['"]""").find(page)?.groupValues?.get(1)?.let { next ->
			page = get(next) ?: return emptyList()
		}
		val hls = parseVoe(page) ?: return emptyList()
		val label = quality.ifBlank { "Voe" }
		return hlsVariants(hls, emptyMap(), label).ifEmpty { listOf(Video(hls, label)) }
	}

	private suspend fun okru(url: String): List<Video> {
		val page = get(url.replace("/video/", "/videoembed/")) ?: return emptyList()
		return parseOkru(page)
	}

	private suspend fun sibnet(url: String, prefix: String): List<Video> {
		val page = get(url) ?: return emptyList()
		val src = Regex("""player\.src\(\s*\[\s*\{\s*src\s*:\s*["']([^"']+)["']""").find(page)?.groupValues?.get(1)
			?: return emptyList()
		val videoUrl = if (src.startsWith("http")) src else "https://video.sibnet.ru$src"
		return listOf(Video(videoUrl, "${prefix}Sibnet", mapOf("Referer" to url)))
	}

	private suspend fun yourUpload(url: String, headers: Map<String, String>, name: String, prefix: String): List<Video> {
		val referer = mapOf("Referer" to "https://www.yourupload.com/")
		val page = get(url, headers + referer) ?: return emptyList()
		val file = Regex("""file\s*:\s*['"]([^'"]+)['"]""").find(page)?.groupValues?.get(1) ?: return emptyList()
		return listOf(Video(file, "$prefix${name.ifBlank { "YourUpload" }}", referer))
	}

	private suspend fun vidBom(url: String): List<Video> {
		val page = get(url) ?: return emptyList()
		val script = JsUnpacker.unpackAndCombine(page) ?: page
		val sources = script.substringAfter("sources:", "").substringBefore("]")
		return Regex("""file\s*:\s*"([^"]+)"(?:\s*,\s*label\s*:\s*"([^"]*)")?""").findAll(sources).map { m ->
			Video(m.groupValues[1], "VidBom ${m.groupValues[2]}".trim())
		}.toList()
	}

	private suspend fun streamlare(url: String, prefix: String, suffix: String): List<Video> {
		val id = url.trimEnd('/').substringAfterLast('/')
		val host = runCatching { URI(url).host }.getOrNull() ?: "slwatch.co"
		val response = transport.execute(
			JsHttpRequest(
				method = "POST",
				url = "https://$host/api/video/stream/get",
				headers = mapOf("Content-Type" to "application/json", "Referer" to url),
				body = """{"id":"$id"}""".toByteArray(),
				options = null,
			),
		)
		if (response.statusCode !in 200..299) return emptyList()
		val result = runCatching { JsHttpBridge.json.parseToJsonElement(response.body).jsonObject["result"] }.getOrNull()
			as? JsonObject ?: return emptyList()
		return result.entries.mapNotNull { (label, value) ->
			val file = (value as? JsonObject)?.get("file")?.jsonPrimitive?.content ?: return@mapNotNull null
			Video(file, "${prefix}Streamlare - $label$suffix".trim())
		}
	}

	// --- shared helpers ---

	private suspend fun request(url: String, headers: Map<String, String> = emptyMap()): JsHttpResponse? = try {
		transport.execute(JsHttpRequest("GET", url, headers, null, null)).takeIf { it.statusCode in 200..299 }
	} catch (e: Exception) {
		if (e is kotlinx.coroutines.CancellationException) throw e
		null
	}

	private suspend fun get(url: String, headers: Map<String, String> = emptyMap()): String? = request(url, headers)?.body

	private suspend fun hlsVariants(
		masterUrl: String,
		headers: Map<String, String>,
		label: String,
		suffix: String = "",
	): List<Video> {
		val playlist = get(masterUrl, headers) ?: return listOf(Video(masterUrl, "$label$suffix", headers))
		val variants = JsVideoExtractorBridge.parseHlsVariants(playlist, masterUrl, "")
		if (variants.isEmpty()) return listOf(Video(masterUrl, "$label - Auto$suffix", headers))
		return variants.map { (variantUrl, quality) ->
			val resolution = quality.substringAfterLast(" - ")
			Video(variantUrl, "$label - $resolution$suffix", headers)
		}
	}

	companion object {

		private const val ALPHANUMERIC = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
		private val M3U8 = Regex("""https?://[^"'\s<>]+?\.m3u8[^"'\s<>]*""")

		/** Finds an HLS playlist URL, unpacking packed player scripts first. */
		fun findM3u8(page: String): String? {
			M3U8.find(page)?.let { return it.value }
			val unpacked = JsUnpacker.unpackAndCombine(page) ?: return null
			return M3U8.find(unpacked)?.value
		}

		/** StreamTape builds its link from two string pieces assigned to `#robotlink`. */
		fun parseStreamTape(page: String): String? {
			val marker = "document.getElementById('robotlink').innerHTML = '"
			val line = page.substringAfter(marker, "").ifEmpty { return null }
			val first = line.substringBefore("'")
			val second = line.substringAfter("+ ('", "").substringBefore("'").ifEmpty { return null }
			// The second literal is cut by one or more chained .substring(n) calls, e.g. ('xcdtoken').substring(1).substring(2)
			val statement = line.substringBefore(';')
			val cut = Regex("""\.substring\((\d+)\)""").findAll(statement).sumOf { it.groupValues[1].toInt() }
			val tail = second.drop(if (cut > 0) cut else 3)
			val url = first + tail
			return if (url.startsWith("//")) "https:$url" else url.takeIf { it.startsWith("http") }
		}

		/** Voe exposes its HLS link either directly or as base64. */
		fun parseVoe(page: String): String? {
			Regex("""['"]hls['"]\s*:\s*['"]([^'"]+)['"]""").find(page)?.groupValues?.get(1)?.let { value ->
				if (value.startsWith("http")) return value
				decodeBase64Text(value)?.takeIf { it.startsWith("http") }?.let { return it }
			}
			return M3U8.find(page)?.value
		}

		/** ok.ru keeps its sources in the JSON `data-options` attribute of the player div. */
		fun parseOkru(page: String): List<Video> {
			val options = Jsoup.parse(page).selectFirst("div[data-options]")?.attr("data-options") ?: return emptyList()
			val metadata = runCatching {
				val root = JsHttpBridge.json.parseToJsonElement(options).jsonObject
				val raw = root["flashvars"]?.jsonObject?.get("metadata")?.jsonPrimitive?.content ?: return emptyList()
				JsHttpBridge.json.parseToJsonElement(raw).jsonObject
			}.getOrNull() ?: return emptyList()
			val videos = (metadata["videos"] as? JsonArray).orEmpty().mapNotNull { element ->
				val video = element as? JsonObject ?: return@mapNotNull null
				val url = video["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
				val name = video["name"]?.jsonPrimitive?.content.orEmpty()
				Video(url, "Okru - ${OKRU_QUALITIES[name] ?: name}")
			}
			val hls = metadata["hlsManifestUrl"]?.jsonPrimitive?.content
			return if (hls != null) listOf(Video(hls, "Okru - Auto")) + videos.reversed() else videos.reversed()
		}

		private val OKRU_QUALITIES = mapOf(
			"mobile" to "144p", "lowest" to "240p", "low" to "360p", "sd" to "480p",
			"hd" to "720p", "full" to "1080p", "quad" to "1440p", "ultra" to "2160p",
		)

		private fun decodeBase64Text(value: String): String? = runCatching { value.decodeBase64()?.utf8() }.getOrNull()
	}
}

internal fun JsVideoExtractors.Video.toJson(): JsonObject = kotlinx.serialization.json.buildJsonObject {
	put("url", kotlinx.serialization.json.JsonPrimitive(url))
	put("originalUrl", kotlinx.serialization.json.JsonPrimitive(url))
	put("quality", kotlinx.serialization.json.JsonPrimitive(quality))
	put("headers", kotlinx.serialization.json.buildJsonObject {
		headers.forEach { (k, v) -> put(k, kotlinx.serialization.json.JsonPrimitive(v)) }
	})
	put("subtitles", kotlinx.serialization.json.buildJsonArray {
		subtitles.forEach { (file, label) ->
			add(kotlinx.serialization.json.buildJsonObject {
				put("file", kotlinx.serialization.json.JsonPrimitive(file))
				put("label", kotlinx.serialization.json.JsonPrimitive(label))
			})
		}
	})
}
