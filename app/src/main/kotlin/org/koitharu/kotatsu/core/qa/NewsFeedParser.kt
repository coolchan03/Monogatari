package org.koitharu.kotatsu.core.qa

import org.koitharu.kotatsu.core.util.DisplayText
import java.io.StringReader
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.xml.sax.InputSource

/** Bounded RSS 2.0 / Atom feed reader. No external entities or DTDs are allowed. */
internal object NewsFeedParser {
    data class Entry(val title: String, val link: String, val date: String, val tags: List<String>)

    fun parse(xml: String): List<Entry> {
        require(xml.length <= 1_000_000) { "Feed is too large" }
        require(!Regex("<!\\s*(DOCTYPE|ENTITY)", RegexOption.IGNORE_CASE).containsMatchIn(xml)) {
            "XML declarations are not allowed"
        }
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isXIncludeAware = false
            isExpandEntityReferences = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val document = factory.newDocumentBuilder().parse(InputSource(StringReader(xml)))
        require(document.documentElement?.tagName in setOf("rss", "feed", "rdf:RDF", "RDF")) {
            "Not an RSS or Atom document"
        }
        val all = document.getElementsByTagName("item")
        val atom = all.length == 0
        val items = if (atom) document.getElementsByTagName("entry") else all
        return (0 until minOf(items.length, 100)).mapNotNull { index ->
            val item = items.item(index) as? Element ?: return@mapNotNull null
            fun contents(name: String) = item.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
            // Feeds often double-escape entities or put them in CDATA (Don&#8217;t).
            fun display(name: String) = DisplayText.clean(contents(name))
            val title = display("title").take(240)
            val linkElement = item.getElementsByTagName("link").item(0) as? Element
            val rawLink = if (atom) linkElement?.getAttribute("href").orEmpty() else contents("link")
            val url = runCatching { URI(rawLink) }.getOrNull()
            if (title.isBlank() || url?.scheme?.equals("https", true) != true || url.host.isNullOrBlank() ||
                url.userInfo != null || url.fragment != null || rawLink.length > 2048) return@mapNotNull null
            val categories = item.getElementsByTagName("category")
            val tags = (0 until minOf(categories.length, 25)).mapNotNull { i ->
                val element = categories.item(i) as? Element ?: return@mapNotNull null
                (if (atom) element.getAttribute("term") else element.textContent)
                    ?.let(DisplayText::clean)?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            }
            Entry(title, checkNotNull(url).toASCIIString(), (if (atom) contents("updated") else contents("pubDate")).take(90), tags)
        }
    }
}
