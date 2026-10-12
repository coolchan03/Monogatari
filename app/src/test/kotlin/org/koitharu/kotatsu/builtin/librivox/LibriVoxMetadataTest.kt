package org.koitharu.kotatsu.builtin.librivox

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LibriVoxMetadataTest {
    @Test fun parsesRealApiAuthorAndGenreStructures() {
        val book = JSONObject("""{
            "authors":[{"first_name":"Alexandre","last_name":"Dumas","id":"431"}],
            "genres":[{"id":"20","name":"Literary Fiction"}]
        }""")
        assertEquals(setOf("Alexandre Dumas"), LibriVoxMetadata.authors(book))
        assertEquals(setOf("Literary Fiction"), LibriVoxMetadata.genres(book))
    }

    @Test fun handlesEmptyArrays() {
        assertEquals(emptySet<String>(), LibriVoxMetadata.authors(JSONObject("{}")))
        assertEquals(emptySet<String>(), LibriVoxMetadata.genres(JSONObject("{}")))
    }
}
