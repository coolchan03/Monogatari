package org.koitharu.kotatsu.core.media

import org.w3c.dom.Element
import org.xml.sax.InputSource
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

/** Safe, bounded RSS/Atom podcast and video-feed parsing. */
object MediaFeedParser {
    enum class Kind { AUDIO, VIDEO }
    data class Episode(
        val title: String, val pageUrl: String?, val mediaUrl: String?,
        val mimeType: String?, val published: String,
        val coverUrl: String?, val kind: Kind,
        /** From itunes:duration, when the feed gives one. */
        val durationSeconds: Int? = null,
        /** Podcasting 2.0 chapters file (podcast:chapters), HTTPS only. */
        val chaptersUrl: String? = null,
        /** Public transcript or subtitle sidecar (Podcasting 2.0). */
        val transcriptUrl: String? = null,
    )

    data class Chapter(val startSeconds: Int, val title: String)

    /** "1:02:03", "42:10" or plain seconds to seconds; null when it isn't a duration. */
    fun parseDuration(text: String?): Int? {
        val parts = text?.trim()?.split(':')?.takeIf { it.isNotEmpty() && it.size <= 3 } ?: return null
        val numbers = parts.map { it.toIntOrNull() ?: return null }
        if (numbers.any { it < 0 }) return null
        return numbers.fold(0) { total, n -> total * 60 + n }.takeIf { it > 0 }
    }

    /** "42 min" or "1 h 05 min" for display. */
    fun formatDuration(seconds: Int): String {
        val minutes = (seconds + 30) / 60
        return if (minutes >= 60) "${minutes / 60} h %02d min".format(minutes % 60) else "$minutes min"
    }

    /** Chapters from a Podcasting 2.0 JSON file: {"chapters":[{"startTime":0,"title":"Intro"}, ...]}. */
    fun parseChapters(json: String): List<Chapter> {
        val array = runCatching { org.json.JSONObject(json).optJSONArray("chapters") }.getOrNull() ?: return emptyList()
        return (0 until minOf(array.length(), 200)).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val start = item.optDouble("startTime", -1.0)
            val title = item.optString("title").trim().take(160)
            if (start < 0 || title.isEmpty() || item.optBoolean("toc", true).not()) null else Chapter(start.toInt(), title)
        }.sortedBy { it.startSeconds }
    }

    /** "0:00", "12:34" or "1:02:03" for a chapter list. */
    fun formatTimestamp(seconds: Int): String {
        val h = seconds / 3600; val m = seconds % 3600 / 60; val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
    fun parse(xml: String): List<Episode> {
        require(xml.length <= 1_000_000) { "RSS feed too large" }
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
            "External entities are forbidden"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            isXIncludeAware = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val root = factory.newDocumentBuilder().parse(InputSource(StringReader(xml))).documentElement
        require(root.tagName in setOf("rss", "feed", "rdf:RDF", "RDF")) { "Not RSS or Atom" }
        val atom = root.tagName == "feed"
        val nodes = root.getElementsByTagName(if (atom) "entry" else "item")
        return (0 until minOf(nodes.length, 120)).mapNotNull { index ->
            val el = nodes.item(index) as? Element ?: return@mapNotNull null
            fun first(name: String) = el.getElementsByTagName(name).item(0) as? Element
            fun text(name: String) = first(name)?.textContent?.trim().orEmpty()
            val title = text("title").take(220).takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val atomLinks = el.getElementsByTagName("link")
            val enclosure = first("enclosure")
                ?: first("media:content")
                ?: (0 until atomLinks.length).mapNotNull { atomLinks.item(it) as? Element }
                    .firstOrNull { it.getAttribute("rel") == "enclosure" }
            val media = https(enclosure?.getAttribute("url"))
                ?: https(enclosure?.getAttribute("href"))
            val mime = enclosure?.getAttribute("type")?.trim()?.takeIf { it.isNotEmpty() }
            val alternate = (0 until atomLinks.length).mapNotNull { atomLinks.item(it) as? Element }
                .firstOrNull { it.getAttribute("rel") in setOf("", "alternate") }
            val page = if (atom) https(alternate?.getAttribute("href"))
                else https(text("link"))
            if (media == null && page == null) return@mapNotNull null
            val kind = if (mime?.startsWith("video/", true) == true ||
                media?.substringBefore('?')?.endsWith(".mp4", true) == true ||
                media?.substringBefore('?')?.endsWith(".webm", true) == true ||
                media?.substringBefore('?')?.endsWith(".m3u8", true) == true ||
                media?.substringBefore('?')?.endsWith(".mpd", true) == true ||
                mime?.contains("dash+xml", true) == true
            ) Kind.VIDEO else Kind.AUDIO
            val cover = https(first("media:thumbnail")?.getAttribute("url"))
                ?: https(first("itunes:image")?.getAttribute("href"))
            val published = if (atom) text("published").ifBlank { text("updated") }
                else text("pubDate")
            Episode(title, page, media, mime, published.take(95), cover, kind,
                durationSeconds = parseDuration(text("itunes:duration")),
                chaptersUrl = https(first("podcast:chapters")?.getAttribute("url")),
                transcriptUrl = https(first("podcast:transcript")?.getAttribute("url"))
                    ?: https(first("media:subTitle")?.getAttribute("href")))
        }
    }

    private fun https(value: String?): String? {
        val candidate = value?.trim()?.takeIf { it.length in 9..2048 } ?: return null
        val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
        return candidate.takeIf { parsed.scheme.equals("https", true) &&
            !parsed.host.isNullOrBlank() && parsed.userInfo == null }
    }
}
