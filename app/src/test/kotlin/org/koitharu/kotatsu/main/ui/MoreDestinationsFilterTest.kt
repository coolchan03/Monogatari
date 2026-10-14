package org.koitharu.kotatsu.main.ui

import org.junit.Assert.*
import org.junit.Test

class MoreDestinationsFilterTest {
    @Test fun searchMatchesTitlesAndDescriptionsWithoutLosingGroups() {
        val filtered = MoreDestinations.filteredSections("API keys")
        assertTrue(filtered.first() is MoreDestinations.Entry.Header)
        assertTrue(filtered.any {
            it is MoreDestinations.Entry.Link && it.page.title == "API keys & accounts"
        })
        assertFalse(filtered.any { it is MoreDestinations.Entry.Link && it.page.title == "Downloads" })
    }

    @Test fun emptySearchKeepsTheOriginalOrder() {
        assertEquals(MoreDestinations.sections, MoreDestinations.filteredSections("  "))
    }

    @Test fun searchThatDoesNotMatchShowsNoGhostHeaders() {
        assertTrue(MoreDestinations.filteredSections("this-does-not-exist-123").isEmpty())
    }
}
