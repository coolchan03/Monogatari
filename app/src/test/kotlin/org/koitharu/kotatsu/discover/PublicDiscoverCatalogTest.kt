package org.koitharu.kotatsu.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.media.PublicBookCatalog

class PublicDiscoverCatalogTest {
    private fun book(title: String, url: String, cover: String? = null) =
        PublicBookCatalog.Book(title, "A. Writer", "Gutenberg", url,
            coverUrl = cover, format = "EPUB")

    @Test fun linksStayOnOfficialHttpsAndCoversMustUseHttps() {
        val page = PublicDiscoverCatalog.asPage(listOf(
            book("Allowed", "https://www.gutenberg.org/ebooks/1", "http://bad.example/cover"),
            book("Unsecured", "http://other.example/book"),
            book("Duplicate", "https://www.gutenberg.org/ebooks/1")
        ), pageSize = 25)
        assertEquals(1, page.items.size)
        assertEquals(null, page.items[0].coverUrl)
        assertEquals("Gutenberg · EPUB", page.items[0].providerLabel)
        assertEquals("https://www.gutenberg.org/ebooks/1", page.items[0].officialUrl)
        assertFalse(page.hasNextPage)
    }

    @Test fun enoughCatalogResultsPermitPagination() {
        val page = PublicDiscoverCatalog.asPage(listOf(
            book("A", "https://example.org/a"),
            book("B", "https://example.org/b")
        ), pageSize = 2)
        assertTrue(page.hasNextPage)
    }
}
