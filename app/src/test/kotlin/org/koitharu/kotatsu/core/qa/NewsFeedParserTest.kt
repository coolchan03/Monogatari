package org.koitharu.kotatsu.core.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

    @Test fun upgradesInsecureArticleLinksToHttps() {
        val xml = """<rss><channel><item><title>Plain</title><link>http://example.org/post</link></item></channel></rss>"""
        assertEquals("https://example.org/post", NewsFeedParser.parse(xml).single().link)
    }

    @Test fun dropsNonWebArticleLinks() {
        val xml = """<rss><channel><item><title>Script</title><link>javascript:alert(1)</link></item></channel></rss>"""
        assertEquals(0, NewsFeedParser.parse(xml).size)
    }

    @Test fun toleratesBomDoctypeAndHtmlEntities() {
        val rss = "\uFEFF\n  <?xml version=\"1.0\"?>\n" +
            "<!DOCTYPE rss PUBLIC \"-//Netscape Communications//DTD RSS 0.91//EN\" \"http://my.netscape.com/publish/formats/rss-0.91.dtd\">" +
            "<rss><channel><item><title>Don&rsquo;t miss&nbsp;it &hellip; Q&A</title>" +
            "<link>https://example.org/a?x=1&y=2</link><pubDate>Tue, 13 Oct 2026 10:00:00 GMT</pubDate></item></channel></rss>"
        val entry = NewsFeedParser.parse(rss).single()
        assertEquals("Don\u2019t miss\u00A0it \u2026 Q&A", entry.title)
        assertEquals("https://example.org/a?x=1&y=2", entry.link)
    }

    @Test fun atomPrefersAlternateLinkAndResolvesRelativeOnes() {
        val atom = """<feed xmlns="http://www.w3.org/2005/Atom"><entry><title>Post</title>
            <link rel="self" href="https://example.org/self.xml"/>
            <link rel="alternate" type="text/html" href="/2026/post"/>
            <updated>2026-10-13T10:00:00.123+02:00</updated></entry></feed>"""
        val entry = NewsFeedParser.parse(atom, "https://example.org/feed.xml").single()
        assertEquals("https://example.org/2026/post", entry.link)
        assertEquals("2026-10-13T10:00:00.123+02:00", entry.date)
    }

    @Test fun rssFallsBackToPermalinkGuid() {
        val rss = """<rss><channel><item><title>Guid only</title><guid>https://example.org/guid</guid></item></channel></rss>"""
        assertEquals("https://example.org/guid", NewsFeedParser.parse(rss).single().link)
    }

    @Test fun cacheRoundTripsAndIgnoresOtherFeeds() {
        val entries = listOf(NewsFeedParser.Entry("Title", "https://example.org/a", "Tue, 13 Oct 2026 10:00:00 GMT", listOf("Anime")))
        val json = NewsFeedParser.encodeCache("https://example.org/feed", entries, 1L)
        assertEquals(entries, NewsFeedParser.decodeCache("https://example.org/feed", json))
        assertEquals(emptyList<NewsFeedParser.Entry>(), NewsFeedParser.decodeCache("https://other.example/feed", json))
        assertEquals(emptyList<NewsFeedParser.Entry>(), NewsFeedParser.decodeCache("https://example.org/feed", "{broken"))
        assertTrue(NewsFeedParser.cacheKey("https://example.org/feed").startsWith("cache_v1:"))
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
