package org.koitharu.kotatsu.builtin.ao3

import org.jsoup.Jsoup

/** HTML parsing is deliberately network-independent, so site changes are testable. */
object Ao3Parser {
    data class SearchResult(
        val id: String,
        val title: String,
        val authors: List<String>,
        val rating: String?,
        val category: String?,
        val fandoms: List<String>,
        val tags: List<String>,
        val warnings: List<String>,
        val summaryHtml: String,
        val words: String?,
        val chapters: String?,
        val kudos: String?,
        val updated: String?,
    )
    data class SearchPage(val works: List<SearchResult>, val hasNextPage: Boolean)
    data class Chapter(val title: String, val url: String, val date: String?)

    fun parseSearch(html: String): SearchPage {
        val doc = Jsoup.parse(html, "https://archiveofourown.org")
        val works = doc.select("li.work.blurb").mapNotNull { li ->
            val link = li.selectFirst("h4.heading a[href^=/works/]") ?: return@mapNotNull null
            val id = Regex("""^/works/(\d+)""").find(link.attr("href"))?.groupValues?.get(1)
                ?: return@mapNotNull null
            val title = link.text().trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            fun list(css: String) = li.select(css).map { it.text().trim() }.filter { it.isNotBlank() }
            SearchResult(
                id = id,
                title = title,
                authors = list("a[rel=author]").ifEmpty { listOf("Anonymous") },
                rating = li.selectFirst("span.rating")?.attr("title")?.ifBlank { null },
                category = li.selectFirst("span.category")?.attr("title")?.ifBlank { null },
                fandoms = list("h5.fandoms a.tag"),
                tags = list("li.relationships a.tag, li.characters a.tag, li.freeforms a.tag"),
                warnings = list("li.warnings a.tag"),
                summaryHtml = li.selectFirst("blockquote.userstuff.summary")?.html().orEmpty(),
                words = li.selectFirst("dd.words")?.text(),
                chapters = li.selectFirst("dd.chapters")?.text(),
                kudos = li.selectFirst("dd.kudos")?.text(),
                updated = li.selectFirst("p.datetime")?.text(),
            )
        }
        return SearchPage(works, doc.selectFirst("li.next a") != null)
    }

    fun parseNavigate(html: String): List<Chapter> {
        val doc = Jsoup.parse(html, "https://archiveofourown.org")
        return doc.select("ol.chapter.index li").mapNotNull { item ->
            val link = item.selectFirst("a[href*=/chapters/]") ?: return@mapNotNull null
            val path = link.attr("href").substringBefore('?')
            if (!path.startsWith("/works/") || !path.contains("/chapters/")) return@mapNotNull null
            Chapter(link.text().trim(), path, item.selectFirst("span.datetime")?.text()?.trim('(', ')', ' '))
        }
    }

    fun parseChapterText(html: String): String {
        val doc = Jsoup.parse(html, "https://archiveofourown.org")
        val body = doc.selectFirst("div#chapters div.userstuff") ?: return ""
        body.select("h3.landmark").remove()
        val notes = doc.select("div.notes .userstuff").joinToString("\n") { it.outerHtml() }
        return body.html() + if (notes.isBlank()) "" else "\n" + notes
    }

    fun isLoginRequired(html: String, finalUrl: String): Boolean =
        finalUrl.contains("/users/login") ||
            Jsoup.parse(html).selectFirst("form#new_user_session") != null
}
