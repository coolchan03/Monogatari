package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaenFreeLibraryTest {
    @Test fun parsesOfficialFreeProductCardsWithoutExternalLinksOrPaidItems() {
        val html = """<ul>
          <li class="product-item"><a class="product-item-link" href="https://www.baen.com/1632.html">1632</a>
            <span class="price">$0.00</span><img class="product-image-photo" src="https://www.baen.com/a.jpg"/></li>
          <li class="product-item"><a class="product-item-link" href="https://www.baen.com/1632.html">1632</a></li>
          <li class="product-item"><a class="product-item-link" href="https://www.baen.com/paid.html">Not free</a>
            <span class="price">$7.99</span></li>
          <li class="product-item"><a class="product-item-link" href="https://example.com/fake">External item</a></li>
          <li class="product-item"><a class="product-item-link" href="javascript:alert(1)">Invalid</a></li>
        </ul>"""
        val result = BaenFreeLibrary.parse(html)
        assertEquals(1, result.size)
        assertEquals("1632", result.single().title)
        assertEquals("https://www.baen.com/1632.html", result.single().url)
        assertEquals("https://www.baen.com/a.jpg", result.single().cover)
    }
    @Test fun emptyPageIsNotInventedAsAWorkingSource() {
        assertTrue(BaenFreeLibrary.parse("<html><body>Unavailable</body></html>").isEmpty())
    }
    @Test fun excludesListingsWithoutProvenZeroPrice() {
        val html = """<li class="product-item"><a class="product-item-link" href="https://www.baen.com/unknown.html">Unknown</a></li>"""
        assertTrue(BaenFreeLibrary.parse(html).isEmpty())
    }
    @Test fun followsThePublishersPagedFreeLibraryCatalog() {
        assertEquals(BaenFreeLibrary.URL, BaenFreeLibrary.pageUrl(1))
        assertEquals("https://www.baen.com/catalog/category/view/s/free-library/id/2012/?p=2",
            BaenFreeLibrary.pageUrl(2))
    }
}
