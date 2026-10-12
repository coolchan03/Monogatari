package org.koitharu.kotatsu.builtin.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource

class NewPipeSearchPlanTest {
    @Test fun youtubeSupportsScopedSearchModes() {
        val channel = NewPipeSearchPlan.create(BuiltinSource.YOUTUBE, "channel: favorite artist")
        assertEquals("favorite artist", channel.query)
        assertEquals(listOf("channels"), channel.filter)
        assertEquals(listOf("playlists"), NewPipeSearchPlan.create(BuiltinSource.YOUTUBE, "PLAYLIST: drama").filter)
    }
    @Test fun nonYoutubeDoesNotAdvertiseUnsupportedMode() {
        val soundcloud = NewPipeSearchPlan.create(BuiltinSource.SOUNDCLOUD, "channel: fiction")
        assertEquals(emptyList<String>(), soundcloud.filter)
        assertEquals("channel: fiction", soundcloud.query)
        assertEquals("audio drama", NewPipeSearchPlan.create(BuiltinSource.SOUNDCLOUD, "").query)
    }
    @Test fun pageCacheKeysAreSourceAndModeSpecific() {
        val a = NewPipeSearchPlan.create(BuiltinSource.YOUTUBE, "playlist:foo")
        val b = NewPipeSearchPlan.create(BuiltinSource.YOUTUBE, "foo")
        val c = NewPipeSearchPlan.create(BuiltinSource.PEERTUBE, "foo")
        assertNotEquals(a.cacheKey,b.cacheKey)
        assertNotEquals(b.cacheKey,c.cacheKey)
    }
}
