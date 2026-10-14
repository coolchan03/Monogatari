package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCatalogMergerTest {
    private fun book(
        title: String, author: String, source: String,
        year: Int? = null, epub: String? = null
    ) = PublicBookCatalog.Book(
        title = title, author = author, catalog = source,
        url = "https://example.org/$source",
        downloadUrl = epub, publicationYear = year
    )

    @Test fun mergedTitlesKeepAllProvidersAndPreferOpenEpub() {
        val copies = listOf(
            book("Jane Eyre", "Charlotte Brontë", "Open Library", 1847),
            book("Jane-Eyre", "Charlotte Brontë", "Gutenberg", 1847, "https://example.org/epub")
        )
        val group = BookCatalogMerger.merge(copies).single()
        assertEquals(2, group.entries.size)
        assertEquals("Gutenberg", group.primary.catalog)
        assertTrue(group.providers.contains("Open Library"))
    }
    @Test fun preferredSourceOverridesAutomaticPrimaryWithoutLosingProviders() {
        val copies = listOf(
            book("Jane Eyre", "Charlotte Brontë", "Open Library", 1847),
            book("Jane-Eyre", "Charlotte Brontë", "Gutenberg", 1847,
                "https://example.org/epub")
        )
        val group = BookCatalogMerger.merge(copies).single()
        assertEquals("Open Library", group.preferred("Open Library").catalog)
        assertEquals("Gutenberg", group.preferred("No longer installed").catalog)
        assertEquals(group.identity, BookCatalogMerger.merge(copies.reversed()).single().identity)
    }

    @Test fun unknownAuthorsAreNotMergedAcrossUnrelatedProviders() {
        val rows = listOf(book("Same title", "", "Source A"), book("Same title", "", "Source B"))
        assertEquals(2, BookCatalogMerger.merge(rows).size)
    }

    @Test fun oldestAndNewestKeepUndatedTitlesLast() {
        val all = BookCatalogMerger.merge(listOf(
            book("No date", "Writer C", "Archive"),
            book("Middle", "Writer B", "Archive", 1970),
            book("Old", "Writer A", "Archive", 1830),
            book("New", "Writer D", "Archive", 2005)
        ))
        assertEquals(
            listOf("Old", "Middle", "New", "No date"),
            BookCatalogMerger.sort(all, BookCatalogSorter.Order.OLDEST).map { it.primary.title }
        )
        assertEquals(
            listOf("New", "Middle", "Old", "No date"),
            BookCatalogMerger.sort(all, BookCatalogSorter.Order.NEWEST).map { it.primary.title }
        )
    }

    @Test fun relevanceKeepsOriginalProviderOrder() {
        val books = listOf(book("Z", "A", "Archive"), book("A", "B", "Archive"))
        assertEquals(books, BookCatalogSorter.sort(books, BookCatalogSorter.Order.RELEVANCE))
    }
}
