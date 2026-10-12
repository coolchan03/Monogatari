package org.koitharu.kotatsu.builtin.scribblehub

import org.jsoup.Jsoup

/** Isolated Scribble Hub selectors, with fixtures for future site-change audits. */
object ScribbleHubParser {
    data class Series(val title: String, val url: String, val cover: String?)
    data class Chapter(val title: String, val url: String)

    fun search(html: String): List<Series> =
        Jsoup.parse(html, "https://www.scribblehub.com")
            .select("div.search_main_box, div.search_result, .search_main_box")
            .mapNotNull { item ->
                val link = item.selectFirst("a[href*=/series/]") ?: return@mapNotNull null
                val url = link.attr("href").substringBefore('?').trimEnd('/')
                if (!url.contains("/series/")) return@mapNotNull null
                val title = link.attr("title").ifBlank { link.text() }.trim()
                if (title.isBlank()) return@mapNotNull null
                Series(title, url, item.selectFirst("img")?.absUrl("src"))
            }.distinctBy { it.url }

    fun chapters(html: String): List<Chapter> =
        Jsoup.parse(html, "https://www.scribblehub.com")
            .select("li.toc_w a[href]").mapNotNull { link ->
                val url = link.attr("href")
                if (url.isBlank()) return@mapNotNull null
                Chapter(link.text().trim(), url)
            }.asReversed()

    fun chapterContent(html: String): String =
        Jsoup.parse(html, "https://www.scribblehub.com")
            .selectFirst("#chp_raw")?.html().orEmpty()
}
