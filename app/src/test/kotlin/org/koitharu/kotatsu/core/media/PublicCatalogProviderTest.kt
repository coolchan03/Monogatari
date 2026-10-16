package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PublicCatalogProviderTest {
    @Test fun allBooksProvidersAreAvailableToUnifiedSearch() {
        val labels = PublicCatalogProvider.entries.map { it.label }
        assertEquals(11, labels.size)
        assertEquals(labels.size, labels.toSet().size)
        assertTrue("Open Library" in labels)
        assertTrue("Gutenberg" in labels)
        assertTrue("StoryWeaver" in labels)
        assertTrue("Wikisource" in labels)
        assertTrue("Standard Ebooks" in labels)
    }

    @Test fun unsupportedCatalogCannotBeImpersonatedByName() {
        assertEquals(PublicCatalogProvider.OPEN_LIBRARY, PublicCatalogProvider.fromLabel("Open Library"))
        assertNull(PublicCatalogProvider.fromLabel("Open Library mirror"))
        assertNull(PublicCatalogProvider.fromLabel(""))
    }
}
