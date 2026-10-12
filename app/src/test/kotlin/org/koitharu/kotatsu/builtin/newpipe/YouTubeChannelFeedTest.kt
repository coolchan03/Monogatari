package org.koitharu.kotatsu.builtin.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeChannelFeedTest {
    private val xml = """<?xml version="1.0" encoding="utf-8"?>
        <feed xmlns:yt="http://www.youtube.com/xml/schemas/2015"
              xmlns="http://www.w3.org/2005/Atom">
          <entry><yt:videoId>AbCdEfG1234</yt:videoId><title>New episode</title>
          <published>2026-10-12T10:00:00+00:00</published></entry>
          <entry><yt:videoId>AbCdEfG1234</yt:videoId><title>Duplicate</title></entry>
          <entry><yt:videoId>bad</yt:videoId><title>Skip invalid</title></entry>
        </feed>"""

    @Test fun parsesRealAtomStructureWithoutDuplicates() {
        val videos = YouTubeChannelFeed.parse(xml)
        assertEquals(1, videos.size)
        assertEquals("New episode", videos.single().title)
        assertEquals("https://www.youtube.com/watch?v=AbCdEfG1234", videos.single().url)
        assertTrue(videos.single().publishedAt > 0L)
    }
    @Test fun validatesChannelIdentifiers() {
        assertTrue(YouTubeChannelFeed.validChannelId("UC_x5XG1OV2P6uZZ5FSM9Ttw"))
        assertFalse(YouTubeChannelFeed.validChannelId("other-host.example/evil"))
        assertEquals("AbCdEfG1234", YouTubeChannelFeed.videoId("https://www.youtube.com/watch?v=AbCdEfG1234&list=x"))
    }
}
