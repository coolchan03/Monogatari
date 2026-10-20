package org.koitharu.kotatsu.builtin.newpipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource

class NewPipeKioskPlanTest {
    // Kiosk ids registered by YoutubeService in NewPipeExtractor v0.26.5; default is "live".
    private val youtubeKiosks = setOf(
        "live",
        "trending_podcasts_episodes",
        "trending_gaming",
        "trending_movies_and_shows",
        "trending_music",
        "Trending",
    )

    @Test fun youtubePrefersChartsOverLiveDefault() {
        val ids = NewPipeKioskPlan.candidates(BuiltinSource.YOUTUBE, youtubeKiosks, "live")
        assertEquals(listOf("trending_movies_and_shows", "trending_music", "trending_gaming"), ids)
    }

    @Test fun youtubeSkipsKiosksTheExtractorDoesNotOffer() {
        val ids = NewPipeKioskPlan.candidates(BuiltinSource.YOUTUBE, setOf("live", "trending_gaming"), "live")
        assertEquals(listOf("trending_gaming", "live"), ids)
    }

    @Test fun lastWorkingKioskIsTriedFirst() {
        val ids = NewPipeKioskPlan.candidates(BuiltinSource.YOUTUBE, youtubeKiosks, "live", lastWorking = "Trending")
        assertEquals("Trending", ids.first())
        assertEquals(ids.distinct(), ids)
        assertTrue(ids.size <= NewPipeKioskPlan.MAX_ATTEMPTS)
    }

    @Test fun otherServicesUseTheirDefaultKiosk() {
        val peertube = NewPipeKioskPlan.candidates(
            BuiltinSource.PEERTUBE,
            setOf("Trending", "Most liked", "Recently added", "Local"),
            "Trending",
        )
        assertEquals("Trending", peertube.first())
        assertEquals(listOf("New & hot"), NewPipeKioskPlan.candidates(BuiltinSource.SOUNDCLOUD, setOf("New & hot"), "New & hot"))
    }

    @Test fun noKiosksMeansSearchFallback() {
        assertEquals(emptyList<String>(), NewPipeKioskPlan.candidates(BuiltinSource.SOUNDCLOUD, emptySet(), null))
        assertEquals(emptyList<String>(), NewPipeKioskPlan.candidates(BuiltinSource.YOUTUBE, emptySet(), "live", "stale"))
    }

    @Test fun cacheKeysAreSourceSpecific() {
        assertNotEquals(
            NewPipeKioskPlan.cacheKey(BuiltinSource.YOUTUBE, "Trending"),
            NewPipeKioskPlan.cacheKey(BuiltinSource.PEERTUBE, "Trending"),
        )
    }
}
