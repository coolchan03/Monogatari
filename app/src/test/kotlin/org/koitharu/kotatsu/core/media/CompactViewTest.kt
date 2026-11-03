package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompactViewTest {

    @Test fun defaultViewKeepsTheExistingColumnCount() {
        // Discover: screenWidthDp / 176, within 2..7.
        assertEquals(2, CompactView.columnsFor(360f, false))
        assertEquals(3, CompactView.columnsFor(560f, false))
        assertEquals(7, CompactView.columnsFor(2000f, false))
        assertEquals(2, CompactView.columnsFor(100f, false))
    }

    @Test fun libraryCellsMatchTheOldFormula() {
        // Library: width / (116dp + 2 * 4dp), within 2..8.
        assertEquals(2, CompactView.columnsFor(360f, false, 124f, 2, 8))
        assertEquals(4, CompactView.columnsFor(520f, false, 124f, 2, 8))
        assertEquals(8, CompactView.columnsFor(1600f, false, 124f, 2, 8))
    }

    @Test fun compactShowsMoreColumnsAtEveryWidth() {
        for (width in listOf(240f, 360f, 600f, 840f, 1200f, 2000f)) {
            val normal = CompactView.columnsFor(width, false)
            val compact = CompactView.columnsFor(width, true)
            assertTrue("width $width: $compact > $normal", compact > normal)
        }
    }

    @Test fun compactIsAboutOnePointThreeTimesMore() {
        assertEquals(3, CompactView.columnsFor(360f, true))
        assertEquals(4, CompactView.columnsFor(528f, true)) // 3 columns -> 4
        assertEquals(5, CompactView.columnsFor(704f, true)) // 4 columns -> 5
        assertEquals(9, CompactView.columnsFor(2000f, true)) // 7 columns -> 9, the cap
        assertEquals(10, CompactView.columnsFor(2000f, true, 124f, 2, 8))
    }

    @Test fun cardWidthShrinksOnlyInCompactView() {
        assertEquals(130, CompactView.cardWidth(130, false))
        assertEquals(100, CompactView.cardWidth(130, true))
    }
}
