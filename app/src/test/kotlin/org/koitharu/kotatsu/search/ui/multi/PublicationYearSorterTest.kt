package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PublicationYearSorterTest {
    @Test fun extractsYearFromAReleaseLabel() {
        assertEquals(1998, PublicationYearSorter.estimateYear(
            "My Story", null, "First published in 1998. The story is set in 2010.",
        ))
    }

    @Test fun extractsBracketedTitleYear() {
        assertEquals(2012, PublicationYearSorter.estimateYear(
            "The Story (2012)", null, null,
        ))
    }

    @Test fun doesNotAssumeHistoricalYearIsPublicationYear() {
        assertNull(PublicationYearSorter.estimateYear(
            "An Old Chronicle", null, "Set in 1905 during a historical event.",
        ))
    }

    @Test fun leavesUnknownDatesUnknown() {
        assertNull(PublicationYearSorter.estimateYear(
            "A Tale Without Dates", null, null,
        ))
    }
}
