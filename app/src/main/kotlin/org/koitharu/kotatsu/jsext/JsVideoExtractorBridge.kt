package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.jsoup.Jsoup
import java.net.URI

/** Sendvid implementation adapted from Mangayomi's SendvidExtractor (Apache-2.0). */
internal class JsVideoExtractorBridge(private val transport: JsHttpTransport) {
    suspend fun handle(name: String, args: JsonArray): String {
        if (name != "sendVidExtractor") throw JsExtensionException("Video extractor not supported: $name")
        val pageUrl = args.getOrNull(0).str().orEmpty()
        if (!pageUrl.startsWith("https://") && !pageUrl.startsWith("http://")) return "[]"
        val headers = args.getOrNull(1).str()?.let { raw ->
            runCatching { JsHttpBridge.json.parseToJsonElement(raw).stringMap() }.getOrNull()
        }.orEmpty()
        val prefix = args.getOrNull(2).str().orEmpty()
        val page = fetch(pageUrl, headers)
        val src = Jsoup.parse(page, pageUrl).selectFirst("source#video_source")?.attr("abs:src")
            ?.takeIf { it.isNotBlank() } ?: return "[]"
        val origin = URI(pageUrl).let { "${it.scheme}://${it.authority}" }
        val common = headers + mapOf("Accept" to "*/*", "Origin" to origin, "Referer" to "$origin/")
        val playlist = fetch(src, common + ("Host" to URI(src).host))
        return parseHlsVariants(playlist, src, prefix).let { videos ->
            buildJsonArray {
                videos.forEach { (url, quality) ->
                    add(buildJsonObject {
                        put("url", url)
                        put("originalUrl", url)
                        put("quality", quality)
                        put("headers", buildJsonObject {
                            (common + ("Host" to URI(url).host)).forEach { (k, v) -> put(k, v) }
                        })
                    })
                }
            }.toString()
        }
    }

    private suspend fun fetch(url: String, headers: Map<String, String>): String {
        val response = transport.execute(JsHttpRequest("GET", url, headers, null, null))
        if (response.statusCode !in 200..299) {
            throw JsExtensionException("Sendvid returned HTTP ${response.statusCode}")
        }
        return response.body
    }

    companion object {
        /** Returns only actual HLS variants, not arbitrary text or malformed URLs. */
        fun parseHlsVariants(text: String, masterUrl: String, prefix: String): List<Pair<String, String>> {
            val lines = text.lines().map { it.trim() }
            return lines.mapIndexedNotNull { index, line ->
                if (!line.startsWith("#EXT-X-STREAM-INF:")) return@mapIndexedNotNull null
                val next = lines.drop(index + 1).firstOrNull { it.isNotBlank() }
                    ?.takeUnless { it.startsWith("#") } ?: return@mapIndexedNotNull null
                val link = runCatching { URI(masterUrl).resolve(next).toString() }.getOrNull()
                    ?: return@mapIndexedNotNull null
                if (!link.startsWith("http://") && !link.startsWith("https://")) return@mapIndexedNotNull null
                val quality = Regex("RESOLUTION=\\d+x(\\d+)").find(line)?.groupValues?.get(1)
                    ?.let { "${it}p" } ?: "Auto"
                link to listOf(prefix, "Sendvid", quality).filter { it.isNotBlank() }.joinToString(" - ")
            }.distinctBy { it.first }
        }
    }
}
