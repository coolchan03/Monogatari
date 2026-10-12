package org.koitharu.kotatsu.builtin.scribblehub

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScribbleHubParserTest {
    private fun fixture(name: String) =
        javaClass.getResourceAsStream("/builtin/scribblehub/$name")!!.bufferedReader().use { it.readText() }

    @Test fun parsesSeriesAndChronologicalToc() {
        assertEquals("The Story", ScribbleHubParser.search(fixture("search.html")).single().title)
        assertEquals(listOf("First", "Second"), ScribbleHubParser.chapters(fixture("toc.html")).map { it.title })
    }
    @Test fun chapterHtml() {
        assertTrue(ScribbleHubParser.chapterContent(fixture("chapter.html")).contains("Chapter content"))
    }
}
