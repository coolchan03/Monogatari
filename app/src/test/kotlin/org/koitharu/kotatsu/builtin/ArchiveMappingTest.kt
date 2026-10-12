package org.koitharu.kotatsu.builtin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveMappingTest {
    @Test fun booksMapCorrectly() {
        val json = JSONObject("""{"response":{"docs":[{"identifier":"demo-123",
            "title":"An Old Book","creator":["Writer A","Writer B"],"subject":["History","Books"]}]}}""")
        val book = ArchiveMapping.mapResults(BuiltinSource.IA_BOOKS, json).single()
        assertEquals("/details/demo-123", book.url)
        assertEquals(setOf("Writer A","Writer B"), book.authors)
        assertTrue(book.tags.any { it.title == "History" })
        assertEquals(book.id, ArchiveMapping.mapResults(BuiltinSource.IA_BOOKS, json).single().id)
    }

    @Test fun sourcesDoNotShareIdentifiers() {
        val json = JSONObject("""{"response":{"docs":[{"identifier":"demo","title":"Item"}]}}""")
        assertTrue(
            ArchiveMapping.mapResults(BuiltinSource.IA_BOOKS, json).single().id !=
            ArchiveMapping.mapResults(BuiltinSource.IA_AUDIO, json).single().id,
        )
    }
}
