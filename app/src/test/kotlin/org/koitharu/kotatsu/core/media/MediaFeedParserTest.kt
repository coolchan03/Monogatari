package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFeedParserTest {
    @Test fun podcastRssEpisodesAndHttpsArtwork() {
        val feed = """<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
            <channel><title>Fiction Podcast</title><item><title>Episode One</title>
            <link>https://fiction.example/one</link>
            <enclosure url="https://cdn.example/episode1.mp3" type="audio/mpeg" />
            <media:thumbnail url="https://cdn.example/cover.png"/>
            <pubDate>Tue, 13 Oct 2026 00:00:00 GMT</pubDate>
            </item></channel></rss>"""
        val item = MediaFeedParser.parse(feed).single()
        assertEquals("Episode One", item.title)
        assertEquals(MediaFeedParser.Kind.AUDIO, item.kind)
        assertEquals("https://cdn.example/episode1.mp3", item.mediaUrl)
        assertEquals("https://cdn.example/cover.png", item.coverUrl)
    }

    @Test fun videoAtomWithEnclosure() {
        val feed = """<feed xmlns="http://www.w3.org/2005/Atom">
            <entry><title>Trailer</title>
            <link rel="alternate" href="https://films.example/trailer" />
            <link rel="enclosure" href="https://cdn.example/trailer.mp4" type="video/mp4" />
            </entry></feed>"""
        val item = MediaFeedParser.parse(feed).single()
        assertEquals(MediaFeedParser.Kind.VIDEO, item.kind)
        assertEquals("https://films.example/trailer", item.pageUrl)
        assertEquals("video/mp4", item.mimeType)
    }

    @Test fun rejectsExternalEntitiesAndInsecureMedia() {
        val unsafe = """<!DOCTYPE rss [<!ENTITY xxe SYSTEM "file:///secrets">]>
            <rss><channel><item><title>&xxe;</title></item></channel></rss>"""
        assertTrue(runCatching { MediaFeedParser.parse(unsafe) }.isFailure)
        val rss = """<rss><channel><item><title>Story</title>
            <link>https://stories.example/post</link>
            <enclosure url="http://insecure.example/audio.mp3" type="audio/mpeg"/>
            </item></channel></rss>"""
        val parsed = MediaFeedParser.parse(rss).single()
        assertNull(parsed.mediaUrl)
        assertEquals("https://stories.example/post", parsed.pageUrl)
    }

    @Test fun durationsChaptersAndFormatting() {
        assertEquals(3723, MediaFeedParser.parseDuration("1:02:03"))
        assertEquals(2530, MediaFeedParser.parseDuration("42:10"))
        assertEquals(90, MediaFeedParser.parseDuration("90"))
        assertNull(MediaFeedParser.parseDuration("soon"))
        assertNull(MediaFeedParser.parseDuration("0"))
        assertEquals("42 min", MediaFeedParser.formatDuration(2530))
        assertEquals("1 h 02 min", MediaFeedParser.formatDuration(3723))
        assertEquals("1:02:03", MediaFeedParser.formatTimestamp(3723))
        assertEquals("0:05", MediaFeedParser.formatTimestamp(5))
        val chapters = MediaFeedParser.parseChapters(
            """{"version":"1.2.0","chapters":[{"startTime":300,"title":"Part two"},{"startTime":0,"title":"Intro"},{"startTime":9,"title":"Hidden","toc":false},{"startTime":-1,"title":"bad"}]}""",
        )
        assertEquals(listOf("Intro", "Part two"), chapters.map { it.title })
        assertEquals(emptyList<MediaFeedParser.Chapter>(), MediaFeedParser.parseChapters("not json"))
    }

    @Test fun feedCarriesDurationAndChaptersUrl() {
        val feed = """<rss version="2.0" xmlns:itunes="x" xmlns:podcast="y"><channel><item><title>Ep</title>
            <link>https://show.example/ep</link><enclosure url="https://cdn.example/e.mp3" type="audio/mpeg"/>
            <itunes:duration>12:30</itunes:duration>
            <podcast:chapters url="https://cdn.example/ch.json" type="application/json+chapters"/></item></channel></rss>"""
        val item = MediaFeedParser.parse(feed).single()
        assertEquals(750, item.durationSeconds)
        assertEquals("https://cdn.example/ch.json", item.chaptersUrl)
    }
}
