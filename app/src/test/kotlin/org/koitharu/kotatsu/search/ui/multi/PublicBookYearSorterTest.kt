package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Test

class PublicBookYearSorterTest {
    private val books = listOf(
        BookCatalogListModel("Without year", "", "OPDS", "https://example.test/unknown"),
        BookCatalogListModel("Old edition", "A", "Archive", "https://example.test/old", 1871),
        BookCatalogListModel("New edition", "B", "Open Library", "https://example.test/new", 2021),
        BookCatalogListModel("Another new edition", "C", "Faded Page", "https://example.test/new2", 2021),
    )

    @Test fun olderAndNewerSortsPutUnknownYearsLast() {
        assertEquals(
            listOf("New edition", "Another new edition", "Old edition", "Without year"),
            PublicBookYearSorter.display(books, PublicationYearOrder.NEWEST_FIRST,
                null, null, true).map { it.title },
        )
        assertEquals(
            listOf("Old edition", "New edition", "Another new edition", "Without year"),
            PublicBookYearSorter.display(books, PublicationYearOrder.OLDEST_FIRST_GLOBAL,
                null, null, true).map { it.title },
        )
    }

    @Test fun publisherYearsFilterWithoutDroppingAllPublicBookResults() {
        assertEquals(
            listOf("New edition", "Another new edition"),
            PublicBookYearSorter.display(books, PublicationYearOrder.RELEVANCE,
                2000, 2025, false).map { it.title },
        )
        assertEquals(
            listOf("Without year", "New edition", "Another new edition"),
            PublicBookYearSorter.display(books, PublicationYearOrder.RELEVANCE,
                2000, 2025, true).map { it.title },
        )
    }

    @Test fun unfilteredPreservesRelevanceAndExcludingUnknownOnlyAffectsMissingDates() {
        assertEquals(books, PublicBookYearSorter.display(
            books, PublicationYearOrder.RELEVANCE, null, null, true,
        ))
        assertEquals(3, PublicBookYearSorter.display(
            books, PublicationYearOrder.RELEVANCE, null, null, false,
        ).size)
    }
}
