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
    )
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
                media?.substringBefore('?')?.endsWith(".webm", true) == true
            ) Kind.VIDEO else Kind.AUDIO
            val cover = https(first("media:thumbnail")?.getAttribute("url"))
                ?: https(first("itunes:image")?.getAttribute("href"))
            Episode(title, page, media, mime, text(if (atom) "updated" else "pubDate").take(95),
                cover, kind)
        }
    }

    private fun https(value: String?): String? {
        val candidate = value?.takeIf { it.length in 9..2048 } ?: return null
        val parsed = runCatching { URI(candidate) }.getOrNull() ?: return null
        return candidate.takeIf { parsed.scheme.equals("https", true) &&
            !parsed.host.isNullOrBlank() && parsed.userInfo == null }
    }
}
