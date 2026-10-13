package org.koitharu.kotatsu.core.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NewsFeedParserTest {
    @Test fun parsesRssAndCategories() {
        val rss = """<rss><channel><item><title>Episode review</title><link>https://example.org/review</link><pubDate>Tuesday</pubDate><category>Reviews</category></item></channel></rss>"""
        val entries = NewsFeedParser.parse(rss)
        assertEquals(1, entries.size)
        assertEquals("Episode review", entries.single().title)
        assertEquals(listOf("Reviews"), entries.single().tags)
        assertEquals("Tuesday", entries.single().date)
    }

    @Test fun parsesAtomLinksAndTermTags() {
        val atom = """<feed><entry><title>Book launch</title><link href="https://example.org/book"/><category term="Books"/></entry></feed>"""
        assertEquals("https://example.org/book", NewsFeedParser.parse(atom).single().link)
        assertEquals(listOf("Books"), NewsFeedParser.parse(atom).single().tags)
    }

    @Test fun dropsInsecureArticleLinks() {
        val xml = """<rss><channel><item><title>Unsafe</title><link>http://example.org</link></item></channel></rss>"""
        assertEquals(0, NewsFeedParser.parse(xml).size)
    }

    @Test fun refusesNonFeedXml() {
        assertThrows(IllegalArgumentException::class.java) {
            NewsFeedParser.parse("<html><body><item><title>Fake</title><link>https://example.org</link></item></body></html>")
        }
    }

    @Test fun rejectsArticleUrlsWithEmbeddedCredentials() {
        val xml = """<rss><channel><item><title>Bad</title><link>https://user:pass@example.org/post</link></item></channel></rss>"""
        assertEquals(0, NewsFeedParser.parse(xml).size)
    }

    @Test fun refusesEntityExpansion() {
        assertThrows(IllegalArgumentException::class.java) {
            NewsFeedParser.parse("<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss/>")
        }
    }
}
