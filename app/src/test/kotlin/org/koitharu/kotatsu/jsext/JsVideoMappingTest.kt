package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JsVideoMappingTest {
    @Test fun acceptsPlayableStreamWithoutOriginalUrl() {
        val item = Json.parseToJsonElement("""{"url":"https://cdn.example.org/video.m3u8","quality":"1080p"}""").jsonObject
        val video = item.toJsVideo()
        assertEquals("https://cdn.example.org/video.m3u8", video?.url)
        assertEquals(video?.url, video?.originalUrl)
    }

    @Test fun preservesProvidedOriginalUrl() {
        val item = Json.parseToJsonElement("""{"url":"https://cdn.example.org/video.mp4","originalUrl":"https://example.org/watch"}""").jsonObject
        assertEquals("https://example.org/watch", item.toJsVideo()?.originalUrl)
    }

    @Test fun ignoresMissingStreamUrl() {
        val item = Json.parseToJsonElement("""{"quality":"720p"}""").jsonObject
        assertNull(item.toJsVideo())
    }
}
