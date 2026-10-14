package org.koitharu.kotatsu.explore.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource

class DefaultSourcePresetsTest {
    @Test fun firstRunSuggestionsSeparateMediaKinds() {
        val presets = DefaultSourcePresets.plan(BuiltinSource.entries, emptyList())
            .associateBy { it.title }
        assertTrue(BuiltinSource.AO3.name in presets.getValue("BL & Yaoi").sources)
        assertTrue(BuiltinSource.SCRIBBLE_HUB.name in presets.getValue("Books & fanfic").sources)
        assertTrue(BuiltinSource.YOUTUBE.name in presets.getValue("Anime & video").sources)
        assertTrue(BuiltinSource.SOUNDCLOUD.name in presets.getValue("Audio").sources)
        assertFalse(BuiltinSource.AO3.name in presets.getValue("Anime & video").sources)
    }

    @Test fun emptySourcesAreNotTurnedIntoEmptyPresets() {
        assertEquals(emptyList<DefaultSourcePresets.Suggestion>(),
            DefaultSourcePresets.plan(emptyList(), emptyList()))
    }

    @Test fun multiMediaPresetCombinesEveryKind() {
        val presets = DefaultSourcePresets.plan(BuiltinSource.entries, emptyList()).associateBy { it.title }
        val all = presets.getValue("Multi-media").sources
        assertTrue(BuiltinSource.YOUTUBE.name in all)
        assertTrue(BuiltinSource.SOUNDCLOUD.name in all)
        assertTrue(BuiltinSource.GUTENBERG.name in all)
    }
}
