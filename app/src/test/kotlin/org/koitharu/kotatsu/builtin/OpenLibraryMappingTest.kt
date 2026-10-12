package org.koitharu.kotatsu.builtin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenLibraryMappingTest {
    private val fixture = JSONObject("""{"docs":[
        {"key":"/works/OL123W","title":"Readable","ebook_access":"public",
         "cover_i":42,"author_name":["Someone"],"subject":["Fiction"]},
        {"key":"/works/OL124W","title":"Borrowable","ebook_access":"borrowable"},
        {"key":"/works/OL125W","title":"Unavailable","ebook_access":"no_ebook"}]}""")

    @Test fun publicOnlyByDefault() {
        val items = OpenLibraryMapping.map(fixture)
        assertEquals(1, items.size)
        assertEquals("Readable", items.single().title)
        assertEquals("https://covers.openlibrary.org/b/id/42-L.jpg", items.single().coverUrl)
        assertTrue(items.single().tags.any { it.title == "Fiction" })
    }

    @Test fun retainsAccessCategoriesWhenUnfiltered() {
        val items = OpenLibraryMapping.map(fixture, readableNowOnly = false)
        assertEquals(3, items.size)
        assertEquals("Borrow on Open Library", items[1].description)
        assertEquals("Not available to read", items[2].description)
    }
}
