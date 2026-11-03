package org.koitharu.kotatsu.builtin.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.MediaType

class NewPipeMoreServicesTest {
    @Test fun newSourcesAreRegisteredWithStableIds() {
        assertTrue(BuiltinSource.MEDIA_CCC in BuiltinSource.entries)
        assertTrue(BuiltinSource.BANDCAMP in BuiltinSource.entries)
        assertEquals("builtin:mediaccc", BuiltinSource.MEDIA_CCC.name)
        assertEquals("builtin:bandcamp", BuiltinSource.BANDCAMP.name)
        assertEquals(MediaType.VIDEO, BuiltinSource.MEDIA_CCC.type)
        assertEquals(MediaType.AUDIO, BuiltinSource.BANDCAMP.type)
    }

    @Test fun mediaCccUsesItsDefaultRecentKiosk() {
        val ids = NewPipeKioskPlan.candidates(BuiltinSource.MEDIA_CCC, setOf("conferences", "live", "recent"), "recent")
        assertEquals("recent", ids.first())
    }

    @Test fun bandcampTriesRadioBeforeAlbumOnlyFeatured() {
        val ids = NewPipeKioskPlan.candidates(BuiltinSource.BANDCAMP, setOf("Featured", "Radio"), "Featured")
        assertEquals(listOf("Radio", "Featured"), ids)
    }

    @Test fun searchFallbackQueriesAreNotBlank() {
        assertEquals("security", NewPipeSearchPlan.create(BuiltinSource.MEDIA_CCC, "").query)
        assertEquals("ambient", NewPipeSearchPlan.create(BuiltinSource.BANDCAMP, null).query)
        assertTrue(NewPipeSearchPlan.create(BuiltinSource.BANDCAMP, "x").filter.isEmpty())
    }
}
