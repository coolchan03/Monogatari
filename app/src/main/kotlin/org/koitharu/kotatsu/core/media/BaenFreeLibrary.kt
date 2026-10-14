package org.koitharu.kotatsu.core.media

import org.jsoup.Jsoup
import java.net.URI

/** Free-library *listings*, not unauthenticated paid-book downloads.
 * Only the publisher's curated Free Library category is read; product pages
 * remain on baen.com for the user's choice of free EPUB or other formats.
 */
internal object BaenFreeLibrary {
    const val URL = "https://www.baen.com/catalog/category/view/s/free-library/id/2012"

    /** Official category uses 12 items per page. Keep pagination on the publisher's site. */
    fun pageUrl(page: Int): String = if (page <= 1) URL else "$URL/?p=$page"

    data class Listing(val title: String, val url: String, val cover: String?)

    fun parse(html: String): List<Listing> = Jsoup.parse(html, URL)
        .select("li.product-item, div.product-item")
        .mapNotNull { card ->
            val link = card.selectFirst("a.product-item-link") ?: return@mapNotNull null
            val title = link.text().trim().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val url = link.absUrl("href")
            val uri = runCatching { URI(url) }.getOrNull() ?: return@mapNotNull null
            if (uri.scheme != "https" || uri.host?.lowercase() !in setOf("www.baen.com", "baen.com")) {
                return@mapNotNull null
            }
            // Bail out if the category unexpectedly contains priced items.
            val price = card.selectFirst(".price")?.text()?.trim().orEmpty()
            val amount = Regex("""\$\s*(\d+(?:\.\d{1,2})?)""").find(price)
                ?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            // A missing/malformed price is not evidence the title is free.
            if (amount != 0.0) return@mapNotNull null
            val img = card.selectFirst("img.product-image-photo")
            val cover = (img?.absUrl("src").orEmpty().ifBlank { img?.absUrl("data-src").orEmpty() })
                .takeIf { it.startsWith("https://") }
            Listing(title, url, cover)
        }
        .distinctBy { it.url }
}
