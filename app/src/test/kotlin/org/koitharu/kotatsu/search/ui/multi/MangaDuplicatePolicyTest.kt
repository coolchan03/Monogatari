package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaDuplicatePolicyTest {
    private fun copy(id: Long, source: String, title: String = "Our Story",
        alt: String? = null, authors: Set<String> = setOf("A. Writer"), kind: String = "MANGA") =
        MangaDuplicatePolicy.Candidate(id, source, title, alt, authors, kind)

    @Test fun identicalCreditedTitlesAcrossSourcesGroupOnce() {
        val a = copy(11, "Manga A")
        val b = copy(22, "Manga B", "OUR STORY")
        val groups = MangaDuplicatePolicy.groups(listOf(a, b))
        assertEquals(1, groups.size)
        assertEquals(2, groups.single().copies.size)
        assertEquals(b, MangaDuplicatePolicy.preferred(groups.single(), "Manga B"))
    }

    @Test fun identicalNamesFromDifferentCreatorsAndMediaRemainSeparate() {
        val a = copy(11, "A")
        val differentAuthor = copy(12, "B", authors = setOf("Other Creator"))
        val differentMedia = copy(13, "C", kind = "BOOK")
        assertTrue(MangaDuplicatePolicy.groups(listOf(a, differentAuthor, differentMedia)).isEmpty())
    }

    @Test fun bareNamesAreTooAmbiguousAndCopiesFromSameSourceNeverGroup() {
        val a = copy(1, "A", authors = emptySet())
        val b = copy(2, "B", authors = emptySet())
        assertTrue(MangaDuplicatePolicy.groups(listOf(a, b)).isEmpty())
        assertTrue(MangaDuplicatePolicy.groups(listOf(copy(1, "A"), copy(2, "A"))).isEmpty())
    }

}
