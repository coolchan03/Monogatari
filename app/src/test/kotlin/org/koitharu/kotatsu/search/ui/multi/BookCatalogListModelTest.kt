package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookCatalogListModelTest {
    @Test fun stableBookIdentityUsesTheActualCatalogUrl() {
        val first = BookCatalogListModel("A book", "An author", "Gutenberg", "https://www.gutenberg.org/ebooks/42")
        val sameBook = first.copy(title = "A book (updated)")
        val different = first.copy(url = "https://www.gutenberg.org/ebooks/43")
        assertTrue(first.areItemsTheSame(sameBook))
        assertFalse(first.areItemsTheSame(different))
    }
}
