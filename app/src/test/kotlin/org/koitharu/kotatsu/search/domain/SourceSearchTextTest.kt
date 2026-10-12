package org.koitharu.kotatsu.search.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource

class SourceSearchTextTest {
    @Test fun ignoresSearchModesForYoutubeResultMatching() {
        assertEquals("cats", BuiltinSource.YOUTUBE.searchTextForResults("channel:cats"))
        assertEquals("cats", BuiltinSource.YOUTUBE.searchTextForResults("PLAYLIST: cats"))
    }
    @Test fun leavesOtherSourcesAlone() {
        assertEquals("channel:cats", BuiltinSource.PEERTUBE.searchTextForResults("channel:cats"))
        assertEquals("cats", BuiltinSource.YOUTUBE.searchTextForResults("cats"))
    }
}
