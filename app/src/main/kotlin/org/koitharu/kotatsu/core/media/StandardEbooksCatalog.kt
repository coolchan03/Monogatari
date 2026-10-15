package org.koitharu.kotatsu.core.media

import org.jsoup.Jsoup
import java.net.URI

/**
 * Public HTML catalog, not Standard Ebooks' patron-only OPDS feed.
 * Parses only explicitly published book links and direct compatible EPUB links.
 */
internal object StandardEbooksCatalog {
    private const val HOST = "standardebooks.org"
    private const val ORIGIN = "https://standardebooks.org"

    fun officialPage(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.host.equals(HOST, ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) && uri.userInfo == null &&
            uri.fragment == null && uri.path.startsWith("/ebooks/") &&
            uri.path.length < 280 && !uri.path.contains("..")
    }

    /** Used only for cover artwork; prevent other origin URLs being injected. */
    private fun officialCover(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme == "https" && uri.host == HOST &&
            uri.userInfo == null && uri.fragment == null &&
            uri.port == -1 && uri.path.startsWith("/images/covers/")
    }

    fun browseUrl(query: String, page: Int = 1): String {
        val safeQuery = java.net.URLEncoder.encode(query.trim().take(120), "UTF-8")
        return "$ORIGIN/ebooks?query=$safeQuery&view=list&per-page=24&page=${page.coerceIn(1, 50)}"
    }

    fun parseListing(html: String): List<PublicBookCatalog.Book> {
        val document = Jsoup.parse(html, ORIGIN)
        return document.select("ol.ebooks-list > li").mapNotNull { entry ->
            val titleLink = entry.selectFirst("a[property='schema:url']:has([property='schema:name'])")
                ?: return@mapNotNull null
            val title = titleLink.selectFirst("[property='schema:name']")?.text()?.trim()
                ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val url = titleLink.absUrl("href").takeIf(::officialPage)
                ?: return@mapNotNull null
            val author = entry.select("p.author").eachText().joinToString(", ").trim()
            val cover = entry.selectFirst("picture img")?.absUrl("src")
                ?.takeIf(::officialCover)
            PublicBookCatalog.Book(
                title = title,
                author = author,
                catalog = "Standard Ebooks",
                url = url,
                coverUrl = cover,
                format = "Compatible EPUB - official edition",
            )
        }.distinctBy { it.url }.take(48)
    }

    /** The supported .epub filename is read from a real listing, never guessed. */
    fun parseCompatibleEpub(html: String, bookUrl: String): String? {
        if (!officialPage(bookUrl)) return null
        val document = Jsoup.parse(html, ORIGIN)
        return document.select("a.epub[property='schema:contentUrl']")
            .asSequence()
            .map { it.absUrl("href") }
            .firstOrNull { url ->
                val path = runCatching { URI(url).path }.getOrNull().orEmpty()
                officialPage(url) && path.contains("/downloads/") &&
                    path.endsWith(".epub", ignoreCase = true) &&
                    !path.endsWith("_advanced.epub", ignoreCase = true) &&
                    !path.endsWith(".kepub.epub", ignoreCase = true) &&
                    url.startsWith(bookUrl.trimEnd('/') + "/downloads/")
            }
    }
}
