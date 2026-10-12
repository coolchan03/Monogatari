package org.koitharu.kotatsu.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.ContentRating

class ContentLevelFilterTest {
    @Test fun filtersPerTitleRating() {
        assertTrue(ContentLevelFilter.allows("SAFE", null))
        assertTrue(ContentLevelFilter.allows("SAFE", ContentRating.SAFE))
        assertFalse(ContentLevelFilter.allows("SAFE", ContentRating.SUGGESTIVE))
        assertFalse(ContentLevelFilter.allows("SAFE", ContentRating.ADULT))
        assertTrue(ContentLevelFilter.allows("SUGGESTIVE", ContentRating.SUGGESTIVE))
        assertFalse(ContentLevelFilter.allows("SUGGESTIVE", ContentRating.ADULT))
        assertTrue(ContentLevelFilter.allows("EVERYTHING", ContentRating.ADULT))
    }
}
