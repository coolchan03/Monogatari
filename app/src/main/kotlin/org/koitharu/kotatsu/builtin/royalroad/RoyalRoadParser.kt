package org.koitharu.kotatsu.builtin.royalroad

import org.jsoup.Jsoup

/** Selector-only Royal Road parsing; network and presentation are separate. */
object RoyalRoadParser {
    data class Fiction(val url: String, val title: String, val cover: String?, val tags: List<String>)
    data class Chapter(val url: String, val title: String, val posted: Long?)

    fun search(html: String): List<Fiction> = Jsoup.parse(html, "https://www.royalroad.com")
        .select("div.fiction-list-item").mapNotNull { item ->
            val link = item.selectFirst("h2.fiction-title a[href^=/fiction/]") ?: return@mapNotNull null
            val url = link.attr("href").substringBefore('?')
            if (!url.startsWith("/fiction/")) return@mapNotNull null
            Fiction(
                url = url,
                title = link.text().trim(),
                cover = item.selectFirst("img")?.absUrl("src")?.takeIf(String::isNotBlank),
                tags = item.select(".tags a").map { it.text().trim() }.filter(String::isNotBlank),
            )
        }

    fun chapters(html: String): List<Chapter> = Jsoup.parse(html, "https://www.royalroad.com")
        .select("table#chapters tbody tr").mapNotNull { row ->
            val link = row.selectFirst("td a[href]") ?: return@mapNotNull null
            val path = link.attr("href").substringBefore('?')
            if (!path.startsWith("/fiction/")) return@mapNotNull null
            Chapter(
                url = path,
                title = link.text().trim(),
                posted = row.selectFirst("time[unixtime]")?.attr("unixtime")?.toLongOrNull(),
            )
        }

    fun chapterContent(html: String): String {
        val doc = Jsoup.parse(html, "https://www.royalroad.com")
        val content = doc.selectFirst(".chapter-content") ?: return ""
        // Royal Road may include injected text with display:none CSS, which must not appear in reader.
        val css = doc.select("style").joinToString("\n") { it.data() + it.html() }
        val hiddenClasses = Regex("""\.([a-zA-Z0-9_-]+)\s*\{[^}]*display\s*:\s*none\s*;?[^}]*}""", RegexOption.IGNORE_CASE)
            .findAll(css).map { it.groupValues[1] }.toSet()
        hiddenClasses.forEach { hidden -> content.select("[class~=$hidden]").remove() }
        content.select("[style*=display:none]").remove()
        return content.html()
    }
}
