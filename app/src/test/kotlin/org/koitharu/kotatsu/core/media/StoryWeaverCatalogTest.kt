package org.koitharu.kotatsu.core.media

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StoryWeaverCatalogTest {
    private val fixture = JSONObject(
        """{
          "ok":true,
          "metadata":{"hits":1},
          "data":[{
            "id":21618,"title":"Wild Cat! Wild Cat!","slug":"21618-wild-cat-wild-cat",
            "language":"English","authors":[{"name":"Sejal Mehta"}],
            "illustrators":[{"name":"Rohan Chakravarty"}],
            "publisher":{"name":"Pratham Books"},
            "coverImage":{"sizes":[{"url":"https://storage.googleapis.com/static.storyweaver.org.in/art/small.jpg"}]}
          }]
        }""".trimIndent()
    )
    @Test fun readsAuthorizedMetadataAndKeepsRealCopyrightCredits() {
        val book = StoryWeaverCatalog.parse(fixture).single()
        assertEquals("Wild Cat! Wild Cat!", book.title)
        assertEquals("Sejal Mehta", book.author)
        assertEquals("StoryWeaver", book.catalog)
        assertEquals("English", book.language)
        assertTrue(book.url.endsWith("/21618-wild-cat-wild-cat"))
        assertTrue(book.attribution!!.contains("Rohan Chakravarty"))
        assertTrue(book.attribution!!.contains("CC BY 4.0"))
        assertEquals(null, book.downloadUrl)
    }

    @Test fun refusesBogusOrUntrustedStoryAddresses() {
        assertTrue(StoryWeaverCatalog.officialStoryUrl(
            "https://storyweaver.org.in/en/stories/21618-wild-cat-wild-cat"))
        assertFalse(StoryWeaverCatalog.officialStoryUrl(
            "https://storyweaver.org.in.attacker.example/en/stories/21618-wild-cat-wild-cat"))
        assertFalse(StoryWeaverCatalog.officialStoryUrl("javascript:alert(1)"))
        assertFalse(StoryWeaverCatalog.officialStoryUrl("http://storyweaver.org.in/en/stories/21618-wild-cat-wild-cat"))
        assertFalse(StoryWeaverCatalog.officialStoryUrl("https://storyweaver.org.in/en/profile"))
    }
    @Test fun invalidSlugsDoNotBecomeClickableSearchResults() {
        val unsafe = JSONObject("""{"ok":true,"data":[{"title":"Invalid","slug":"../bad"}]}""")
        assertTrue(StoryWeaverCatalog.parse(unsafe).isEmpty())
        assertTrue(StoryWeaverCatalog.parse(JSONObject("""{"ok":false,"data":[]}""")).isEmpty())
    }
}
