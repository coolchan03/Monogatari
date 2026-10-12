package org.koitharu.kotatsu.search.ui.multi

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource

class VideoResultModeTest {
    @Test fun youtubeChannelsHaveDedicatedSearch() {
        assertEquals("channel:cats", VideoResultMode.CHANNELS.queryFor(BuiltinSource.YOUTUBE, "cats"))
        assertEquals("playlist:cats", VideoResultMode.PLAYLISTS.queryFor(BuiltinSource.YOUTUBE, "cats"))
    }
    @Test fun otherSourcesNeverReceiveYoutubeFilters() {
        assertEquals("cats", VideoResultMode.CHANNELS.queryFor(BuiltinSource.PEERTUBE, "cats"))
        assertEquals("cats", VideoResultMode.PLAYLISTS.queryFor(BuiltinSource.SOUNDCLOUD, "cats"))
    }
    @Test fun changesModeWithoutStackingPrefixes() {
        assertEquals("playlist:cats", VideoResultMode.PLAYLISTS.queryFor(BuiltinSource.YOUTUBE, "channel:cats"))
        assertEquals("cats", VideoResultMode.VIDEOS.queryFor(BuiltinSource.YOUTUBE, "playlist:cats"))
    }
}
