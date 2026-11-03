package org.koitharu.kotatsu.builtin.podcasts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PodcastDirectoryParserTest {
    private val search = """
        {"resultCount":4,"results":[
          {"collectionName":"Night Show","artistName":"A. Host","feedUrl":"https://example.com/feed.xml",
           "artworkUrl600":"https://img.example.com/600.jpg","primaryGenreName":"Fiction"},
          {"collectionName":"Insecure","artistName":"B","feedUrl":"http://example.com/plain.xml"},
          {"collectionName":"Night Show copy","feedUrl":"https://example.com/feed.xml"},
          {"trackName":"Fallback title","artworkUrl100":"http://bad/img.jpg","feedUrl":"https://example.org/rss"}
        ]}
    """.trimIndent()

    @Test fun keepsOnlyHttpsDistinctFeeds() {
        val items = PodcastDirectoryParser.parseResults(search)
        assertEquals(listOf("https://example.com/feed.xml", "https://example.org/rss"), items.map { it.feedUrl })
        assertEquals("Night Show", items[0].title)
        assertEquals("A. Host", items[0].author)
        assertEquals("https://img.example.com/600.jpg", items[0].artworkUrl)
        assertEquals("Fiction", items[0].genre)
        assertEquals("Fallback title", items[1].title)
        assertNull(items[1].artworkUrl)
    }

    @Test fun emptyOrMissingResultsAreEmpty() {
        assertEquals(0, PodcastDirectoryParser.parseResults("""{"resultCount":0}""").size)
        assertEquals(0, PodcastDirectoryParser.parseResults("""{"results":[]}""").size)
    }

    @Test fun topIdsComeFromImId() {
        val json = """
            {"feed":{"entry":[
              {"id":{"label":"x","attributes":{"im:id":"123"}}},
              {"id":{"label":"y","attributes":{"im:id":"abc"}}},
              {"id":{"label":"z","attributes":{"im:id":"456"}}}
            ]}}
        """.trimIndent()
        assertEquals(listOf("123", "456"), PodcastDirectoryParser.parseTopIds(json))
        assertEquals(emptyList<String>(), PodcastDirectoryParser.parseTopIds("""{"feed":{}}"""))
    }
}
