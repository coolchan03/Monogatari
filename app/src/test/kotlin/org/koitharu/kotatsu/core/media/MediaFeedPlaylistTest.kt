package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFeedPlaylistTest {
    private fun episode(title: String, url: String) = MediaFeedParser.Episode(
        title = title, pageUrl = "https://fiction.example/${title.lowercase()}",
        mediaUrl = url, mimeType = "audio/mpeg",
        published = "2026-10-14",
        coverUrl = null, kind = MediaFeedParser.Kind.AUDIO,
        durationSeconds = 120, transcriptUrl = "https://fiction.example/captions.vtt",
    )

    @Test fun saveRemoveAndRoundTripMetadata() {
        val first = episode("First", "https://cdn.example/first.mp3")
        val second = episode("Second", "https://cdn.example/second.mp3")
        val saved = MediaFeedPlaylist.add(MediaFeedPlaylist.add(emptyList(), first), second)
        val deduplicated = MediaFeedPlaylist.add(saved, first)
        assertEquals(2, deduplicated.size)
        assertEquals(first, deduplicated.first())
        val restored = MediaFeedPlaylist.decode(MediaFeedPlaylist.encode(deduplicated))
        assertEquals(deduplicated, restored)
        assertTrue(MediaFeedPlaylist.contains(restored, first))
        assertFalse(MediaFeedPlaylist.contains(MediaFeedPlaylist.remove(restored, first), first))
    }

    @Test fun brokenOrInsecureSavedEntriesAreNotRestored() {
        assertTrue(MediaFeedPlaylist.decode("not JSON").isEmpty())
        val data = """[{"title":"Not safe","media":"http://unsafe.example/e.mp3",
            "page":"http://unsafe.example/page","kind":"AUDIO"},
            {"title":"Safe","media":"https://safe.example/show.mp3",
            "page":"https://safe.example/page","kind":"AUDIO"}]"""
        val rows = MediaFeedPlaylist.decode(data)
        assertEquals(1, rows.size)
        assertEquals("Safe", rows.single().title)
    }

    @Test fun limitsSavedEpisodes() {
        val entries = (1..205).map { i -> episode("Ep$i", "https://cdn.example/$i.mp3") }
        val saved = entries.fold(emptyList<MediaFeedParser.Episode>()) { list, next ->
            MediaFeedPlaylist.add(list, next)
        }
        assertEquals(200, saved.size)
    }
}
