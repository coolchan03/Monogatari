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

    @Test fun titleKeyIgnoresCasePunctuationAndBracketedTags() {
        assertEquals("given", PublicationYearSorter.titleKey("Given (2019) [Official]"))
        assertEquals(PublicationYearSorter.titleKey("Given: Manga!"), PublicationYearSorter.titleKey("given manga"))
        assertEquals("", PublicationYearSorter.titleKey("(2019)"))
    }

    @Test fun globalSortMergesSourcesDropsDuplicatesAndKeepsUnknownLast() {
        val sourceA = listOf("Alpha (2010)", "Beta", "Gamma (1999)")
        val sourceB = listOf("alpha", "Delta (2005)")
        val years = mapOf("Alpha (2010)" to 2010, "alpha" to 2010, "Gamma (1999)" to 1999, "Delta (2005)" to 2005)
        val oldest = PublicationYearSorter.sortedGlobal(
            listOf(sourceA, sourceB), PublicationYearOrder.OLDEST_FIRST_GLOBAL, { it }, { years[it] },
        )
        assertEquals(listOf("Gamma (1999)", "Delta (2005)", "Alpha (2010)", "Beta"), oldest)
        val newest = PublicationYearSorter.sortedGlobal(
            listOf(sourceA, sourceB), PublicationYearOrder.NEWEST_FIRST_GLOBAL, { it }, { years[it] },
        )
        assertEquals(listOf("Alpha (2010)", "Delta (2005)", "Gamma (1999)", "Beta"), newest)
    }

    @Test fun globalSortCanRetainUnlikeCreatorsSharingATitle() {
        val results = PublicationYearSorter.sortedGlobal(
            listOf(listOf("One Story", "One Story")), PublicationYearOrder.NEWEST_FIRST_GLOBAL,
            { it }, { 2007 }, deduplicate = false,
        )
        assertEquals(2, results.size)
    }

    @Test fun orderFlags() {
        assertEquals(true, PublicationYearOrder.OLDEST_FIRST_GLOBAL.isGlobal)
        assertEquals(false, PublicationYearOrder.NEWEST_FIRST.isGlobal)
        assertEquals(true, PublicationYearOrder.NEWEST_FIRST_GLOBAL.isNewestFirst)
    }

    @Test fun yearFilterRangeAndUnknowns() {
        assertEquals(true, YearFilter.matches(1995, 1990, 2000, false))
        assertEquals(false, YearFilter.matches(2005, 1990, 2000, true))
        assertEquals(true, YearFilter.matches(2005, 1990, null, false))
        assertEquals(true, YearFilter.matches(null, 1990, 2000, true))
        assertEquals(false, YearFilter.matches(null, 1990, 2000, false))
        assertEquals(false, YearFilter.isActive(null, null, true))
        assertEquals(true, YearFilter.isActive(null, null, false))
    }

    @Test fun yearBadgeDistinguishesStatedFromGuessed() {
        val stated = PublicationYearSorter.estimateDetailed("My Story", null, "First published in 1998. Set in 2010.")
        assertEquals("1998", stated?.badge)
        val guessed = PublicationYearSorter.estimateDetailed("The Story (2012)", null, null)
        assertEquals("~2012", guessed?.badge)
        assertNull(PublicationYearSorter.estimateDetailed("No Dates", null, null))
    }

    @Test fun languageFilterKeepsMultiLanguageSources() {
        assertEquals(true, LanguageFilter.matches("en", null))
        assertEquals(true, LanguageFilter.matches("EN", "en"))
        assertEquals(false, LanguageFilter.matches("ja", "en"))
        assertEquals(true, LanguageFilter.matches(null, "en"))
        assertEquals(listOf("en", "ja"), LanguageFilter.available(listOf("ja", null, "en", "EN", "")))
    }
}
