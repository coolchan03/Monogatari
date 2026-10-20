package org.koitharu.kotatsu.core.qa

import org.json.JSONArray
import org.json.JSONObject
import org.koitharu.kotatsu.core.util.DisplayText
import org.koitharu.kotatsu.core.util.FeedXml
import org.w3c.dom.Element

/** Bounded RSS 2.0 / RSS 1.0 / Atom feed reader. Entity declarations and DTDs are refused (see [FeedXml]). */
internal object NewsFeedParser {
    const val MAX_XML_CHARS = 2_000_000
    /** Articles kept per feed in the offline cache shown while a refresh runs. */
    const val MAX_CACHED_ENTRIES = 60
    private const val CACHE_KEY_PREFIX = "cache_v1:"

    data class Entry(val title: String, val link: String, val date: String, val tags: List<String>)

    /** [baseUrl] (the feed address) resolves relative article links. */
    fun parse(xml: String, baseUrl: String? = null): List<Entry> {
        require(xml.length <= MAX_XML_CHARS) { "Feed is too large" }
        val document = FeedXml.parseDocument(xml)
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
            if (title.isBlank()) return@mapNotNull null
            val candidates = if (atom) atomLinks(item) else rssLinks(item)
            val link = candidates.firstNotNullOfOrNull { FeedXml.articleUrl(it, baseUrl) } ?: return@mapNotNull null
            val categories = item.getElementsByTagName("category")
            val tags = (0 until minOf(categories.length, 25)).mapNotNull { i ->
                val element = categories.item(i) as? Element ?: return@mapNotNull null
                (if (atom) element.getAttribute("term") else element.textContent)
                    ?.let(DisplayText::clean)?.trim()?.take(80)?.takeIf { it.isNotEmpty() }
            }
            val date = if (atom) contents("updated").ifBlank { contents("published") }
                else contents("pubDate").ifBlank { contents("dc:date") }
            Entry(title, link, date.take(90), tags)
        }
    }

    /** Atom: rel="alternate" (or no rel) HTML links first, then any link that isn't self/enclosure. */
    private fun atomLinks(item: Element): List<String> {
        val nodes = item.getElementsByTagName("link")
        val links = (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }
            .filter { it.getAttribute("href").isNotBlank() }
        fun alternate(link: Element) = link.getAttribute("rel").let { it.isEmpty() || it == "alternate" }
        fun html(link: Element) = link.getAttribute("type").let { it.isEmpty() || it.contains("html", true) }
        return (links.filter { alternate(it) && html(it) } + links.filter(::alternate) +
            links.filter { it.getAttribute("rel") !in setOf("self", "enclosure", "replies", "edit") })
            .map { it.getAttribute("href") }.distinct()
    }

    /** RSS: <link>, then FeedBurner's original link, then a permalink <guid>. */
    private fun rssLinks(item: Element): List<String> {
        fun text(name: String) = item.getElementsByTagName(name).item(0)?.textContent?.trim().orEmpty()
        val guid = item.getElementsByTagName("guid").item(0) as? Element
        val permalink = guid?.takeIf { !it.getAttribute("isPermaLink").equals("false", true) }?.textContent?.trim()
        return listOfNotNull(text("link"), text("feedburner:origLink"), permalink, item.getAttribute("rdf:about"))
            .filter { it.isNotBlank() }
    }

    /** SharedPreferences key holding the last articles of [feedUrl]. */
    fun cacheKey(feedUrl: String): String = CACHE_KEY_PREFIX + Integer.toHexString(feedUrl.hashCode())

    fun encodeCache(feedUrl: String, entries: List<Entry>, savedAt: Long): String {
        val array = JSONArray()
        entries.take(MAX_CACHED_ENTRIES).forEach { entry ->
            array.put(JSONObject().apply {
                put("t", entry.title.take(240))
                put("l", entry.link)
                put("d", entry.date.take(90))
                put("g", JSONArray(entry.tags.take(25)))
            })
        }
        return JSONObject().put("url", feedUrl).put("at", savedAt).put("items", array).toString()
    }

    /** Cached articles of [feedUrl]; empty when missing, corrupt or written for another feed. */
    fun decodeCache(feedUrl: String, json: String?): List<Entry> {
        if (json.isNullOrBlank() || json.length > 400_000) return emptyList()
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        if (root.optString("url") != feedUrl) return emptyList()
        val array = root.optJSONArray("items") ?: return emptyList()
        return (0 until minOf(array.length(), MAX_CACHED_ENTRIES)).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val title = item.optString("t").trim().take(240).takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val link = item.optString("l").takeIf { it.startsWith("https://") }
                ?.let { FeedXml.articleUrl(it) } ?: return@mapNotNull null
            val tagArray = item.optJSONArray("g")
            val tags = if (tagArray == null) emptyList() else (0 until minOf(tagArray.length(), 25))
                .mapNotNull { tagArray.optString(it).trim().take(80).takeIf { tag -> tag.isNotEmpty() } }
            Entry(title, link, item.optString("d").take(90), tags)
        }
    }
}
