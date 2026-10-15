package org.koitharu.kotatsu.core.media

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFeedPlaybackSpecTest {

    @Test fun subtitleSidecarsOnlyAcceptSupportedHttpsFormats() {
        assertEquals(MimeTypes.TEXT_VTT,
            MediaFeedPlaybackSpec.subtitleMimeType("https://cdn.example/ep.VTT?auth=123"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP,
            MediaFeedPlaybackSpec.subtitleMimeType("https://cdn.example/captions.srt"))
        assertEquals(MimeTypes.APPLICATION_TTML,
            MediaFeedPlaybackSpec.subtitleMimeType("https://cdn.example/subs.ttml"))
        assertEquals(MimeTypes.TEXT_SSA,
            MediaFeedPlaybackSpec.subtitleMimeType("https://cdn.example/subs.ass"))
        assertNull(MediaFeedPlaybackSpec.subtitleMimeType("https://cdn.example/transcript.html"))
        assertNull(MediaFeedPlaybackSpec.subtitleMimeType("http://cdn.example/captions.vtt"))
    }

    @Test fun rejectCredentialsFragmentsAndUntrustedSchemes() {
        assertFalse(MediaFeedPlaybackSpec.validStream(null))
        assertFalse(MediaFeedPlaybackSpec.validStream("file:///sdcard/secret.vtt"))
        assertFalse(MediaFeedPlaybackSpec.validStream("https://user:pass@example.com/stream.mp3"))
        assertFalse(MediaFeedPlaybackSpec.validStream("https://example.com/stream.mp3#extra"))
        assertFalse(MediaFeedPlaybackSpec.validStream("https://example.com:8080/video.mp4"))
        assertTrue(MediaFeedPlaybackSpec.validStream("https://cdn.example/episode.mp3"))
        assertTrue(MediaFeedPlaybackSpec.validStream("https://cdn.example/episode.m3u8?token=x"))
    }

    @Test fun chapterSeekingUsesMillisecondsAndClampsNegativePositions() {
        assertEquals(125000L, MediaFeedPlaybackSpec.chapterPositionMs(125))
        assertEquals(0L, MediaFeedPlaybackSpec.chapterPositionMs(-1))
        assertEquals(3600000L, MediaFeedPlaybackSpec.chapterPositionMs(3600))
    }
}
