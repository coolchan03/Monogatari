package org.koitharu.kotatsu.builtin.ao3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Ao3ParserTest {
    private fun fixture(name: String): String =
        javaClass.getResourceAsStream("/builtin/ao3/$name")!!.bufferedReader().use { it.readText() }

    @Test fun searchExtractsWorkMetadata() {
        val page = Ao3Parser.parseSearch(fixture("search-fixture.html"))
        assertTrue(page.hasNextPage)
        val work = page.works.single()
        assertEquals("12345", work.id)
        assertEquals("A Sample Work", work.title)
        assertEquals(listOf("A Writer"), work.authors)
        assertEquals("M/M", work.category)
        assertEquals("Teen And Up Audiences", work.rating)
        assertEquals("2/3", work.chapters)
        assertTrue(work.tags.contains("Characters A/B"))
    }

    @Test fun navigateAndChapterCleanLandmarks() {
        val chapters = Ao3Parser.parseNavigate(fixture("navigate-fixture.html"))
        assertEquals(2, chapters.size)
        assertEquals("/works/12345/chapters/988", chapters[1].url)
        val content = Ao3Parser.parseChapterText(fixture("chapter-fixture.html"))
        assertTrue(content.contains("Example chapter text"))
        assertTrue(content.contains("Author note"))
        assertFalse(content.contains("Hidden landmark"))
    }

    @Test fun detectsLoginGate() {
        assertTrue(Ao3Parser.isLoginRequired("", "https://archiveofourown.org/users/login"))
        assertFalse(Ao3Parser.isLoginRequired(fixture("chapter-fixture.html"), "https://archiveofourown.org/works/12345"))
    }
}
