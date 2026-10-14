package org.koitharu.kotatsu.discover

import org.koitharu.kotatsu.core.media.PublicBookCatalog

/** Display verified catalog links; unlike AniList title suggestions these open official listings. */
internal object PublicDiscoverCatalog {
    fun asPage(books: List<PublicBookCatalog.Book>, pageSize: Int): AniListDiscover.Page =
        AniListDiscover.Page(
            books.filter { it.title.isNotBlank() && it.url.startsWith("https://") }
                .distinctBy { it.url }
                .map { book ->
                    AniListDiscover.Item(
                        id = book.url.hashCode(),
                        title = book.title,
                        alternativeTitle = null,
                        coverUrl = book.coverUrl?.takeIf { it.startsWith("https://") },
                        score = null,
                        officialUrl = book.url,
                        providerLabel = listOfNotNull(book.catalog, book.format).joinToString(" · "),
                    )
                },
            books.size >= pageSize,
        )
}
