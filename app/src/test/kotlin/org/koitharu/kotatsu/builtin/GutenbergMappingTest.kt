package org.koitharu.kotatsu.builtin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.ContentRating

class GutenbergMappingTest {
    @Test fun gutendexResultsBecomeStableBookEntries() {
        val json = JSONObject("""{"results":[{"id":1342,"title":"Pride and Prejudice",
            "authors":[{"name":"Austen, Jane"}],"subjects":["Fiction"],
            "formats":{"image/jpeg":"https://example.test/cover.jpg"}}]}""")
        val first = GutenbergMapping.mapBooks(json).single()
        val second = GutenbergMapping.mapBooks(json).single()
        assertEquals(first.id, second.id)
        assertEquals(BuiltinSource.GUTENBERG, first.source)
        assertEquals("Pride and Prejudice", first.title)
        assertEquals("/ebooks/1342", first.url)
        assertEquals(setOf("Austen, Jane"), first.authors)
        assertTrue(first.tags.any { it.title == "Fiction" })
        assertEquals(ContentRating.SAFE, first.contentRating)
    }

    @Test fun missingFieldsAreSkipped() {
        assertTrue(GutenbergMapping.mapBooks(JSONObject("""{"results":[{"title":"No ID"}]}""")).isEmpty())
    }
}
