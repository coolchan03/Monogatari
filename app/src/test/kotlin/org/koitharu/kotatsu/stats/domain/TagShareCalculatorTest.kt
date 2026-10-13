package org.koitharu.kotatsu.stats.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.stats.data.TagDurationRow

class TagShareCalculatorTest {
    @Test fun independentOverlappingTagsUseWholeLibraryDenominator() {
        val shares = TagShareCalculator.compute(
            listOf(TagDurationRow("Boys' Love", 37_000L),
                TagDurationRow("Romance", 65_000L)), 100_000L)
        assertEquals("Romance", shares.first().title)
        assertEquals(0.37, shares.last().percent, 0.00001)
        assertTrue(shares.sumOf { it.percent } > 1.0)
    }

    @Test fun emptyOrInvalidDurationsAreIgnoredAndRatiosClamped() {
        assertTrue(TagShareCalculator.compute(
            listOf(TagDurationRow("BL", 100L)), 0L).isEmpty())
        val result = TagShareCalculator.compute(
            listOf(TagDurationRow(" ", 100L), TagDurationRow("BL", -1L),
                TagDurationRow("Fantasy", 150L)), 100L)
        assertEquals(1, result.size)
        assertEquals(1.0, result.first().percent, 0.00001)
    }
}
