package org.koitharu.kotatsu.builtin.royalroad

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RoyalRoadParserTest {
    private fun fixture(name: String) =
        javaClass.getResourceAsStream("/builtin/royalroad/$name")!!.bufferedReader().use { it.readText() }

    @Test fun searchExtractsFiction() {
        val fiction = RoyalRoadParser.search(fixture("search-fixture.html")).single()
        assertEquals("A Dragon Story", fiction.title)
        assertEquals("/fiction/123/a-dragon-story", fiction.url)
        assertEquals(listOf("Fantasy", "Adventure"), fiction.tags)
    }

    @Test fun navigationExtractsOrderedChapters() {
        val chapters = RoyalRoadParser.chapters(fixture("fiction-fixture.html"))
        assertEquals(2, chapters.size)
        assertEquals(1760000000L, chapters[0].posted)
        assertEquals("Chapter Two", chapters[1].title)
    }

    @Test fun hiddenTextIsNotRead() {
        val html = RoyalRoadParser.chapterContent(fixture("chapter-fixture.html"))
        assertTrue(html.contains("Real chapter text"))
        assertTrue(html.contains("More real text"))
        assertFalse(html.contains("Hidden nuisance line"))
    }
}
