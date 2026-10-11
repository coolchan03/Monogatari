package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Test

class JsVideoExtractorBridgeTest {
    @Test fun extractsRelativeHlsVariants() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=400000,RESOLUTION=1280x720\n720/index.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=800000,RESOLUTION=1920x1080\n1080/index.m3u8\n"
        val streams = JsVideoExtractorBridge.parseHlsVariants(master, "https://cdn.example.org/live/master.m3u8", "My source")
        assertEquals(2, streams.size)
        assertEquals("https://cdn.example.org/live/720/index.m3u8", streams[0].first)
        assertEquals("My source - Sendvid - 720p", streams[0].second)
    }

    @Test fun excludesDuplicates() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=10\nhttps://cdn.example.org/a.m3u8\n#EXT-X-STREAM-INF:BANDWIDTH=10\nhttps://cdn.example.org/a.m3u8"
        assertEquals(1, JsVideoExtractorBridge.parseHlsVariants(master, "https://cdn.example.org/master.m3u8", "").size)
    }
}
