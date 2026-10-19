package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMetadataFiltersTest {
    @Test fun filtersOnlyDeclaredGenresNotUnrelatedTitles() {
        assertTrue(SearchMetadataFilters.tagMatches(listOf("Boys' Love", "Romance"), "romance"))
        assertFalse(SearchMetadataFilters.tagMatches(listOf("Mystery", "Horror"), "romance"))
        assertTrue(SearchMetadataFilters.tagMatches(emptyList(), null))
    }
    @Test fun historyRequiresActualLocalData() {
        val ids = setOf(10L)
        assertTrue(SearchMetadataFilters.historyMatches(10, SearchMetadataFilters.History.VIEWED, ids))
        assertFalse(SearchMetadataFilters.historyMatches(11, SearchMetadataFilters.History.VIEWED, ids))
        assertTrue(SearchMetadataFilters.historyMatches(11, SearchMetadataFilters.History.UNVIEWED, ids))
        assertFalse(SearchMetadataFilters.historyMatches(11, SearchMetadataFilters.History.UNVIEWED, null))
    }
    @Test fun sourceLanguageCodesMatchProviderLanguageNames() {
        assertTrue(SearchMetadataFilters.languageMatches("English", "en"))
        assertTrue(SearchMetadataFilters.languageMatches("en", "en"))
        assertTrue(SearchMetadataFilters.languageMatches("en", "English"))
        assertFalse(SearchMetadataFilters.languageMatches("Japanese", "en"))
        assertFalse(SearchMetadataFilters.languageMatches(null, "en"))
        assertFalse(SearchMetadataFilters.languageMatches("", "en"))
        assertTrue(SearchMetadataFilters.languageMatches(null, null))
    }
    @Test fun filtersCatalogFormatsWithoutPretendingUnknownIsEpub() {
        assertTrue(SearchMetadataFilters.formatMatches("EPUB", "epub"))
        assertFalse(SearchMetadataFilters.formatMatches("Online listing", "epub"))
        assertFalse(SearchMetadataFilters.formatMatches(null, "epub"))
        assertEquals(listOf("EPUB", "PDF"), SearchMetadataFilters.availableFormats(listOf("PDF", "EPUB", null, "epub")))
    }
}
