package org.koitharu.kotatsu.reader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslationPoliciesTest {
    @Test fun novelChunksRemainBoundedAndReadable() {
        val text = "hello ".repeat(700).trim()
        val chunks = TranslationChunker.split(text)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 1500 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test fun explicitSeriesChoiceWinsOverLanguageDefault() {
        assertFalse(SeriesTranslationPreferences.enabled(false, "ja"))
        assertTrue(SeriesTranslationPreferences.enabled(true, "en"))
        assertTrue(SeriesTranslationPreferences.enabled(null, "ja-JP"))
        assertFalse(SeriesTranslationPreferences.enabled(null, "en-US"))
    }

    @Test fun glossaryFavorsLongestNames() {
        assertEquals("Captain Hana greets Hana", SeriesTranslationPreferences.glossaryReplace(
            "Hana-chan greets Hana", mapOf("Hana" to "Hana", "Hana-chan" to "Captain Hana")))
    }

    @Test fun usesExistingEnglishAlternateWithoutChangingOriginal() {
        assertEquals("A Story", TranslationTextPolicy.preferredEnglishTitle("物語", listOf("物語", "A Story")))
    }

    @Test fun stableHashSeparatesLanguagesAndText() {
        val a = TranslationTextPolicy.sha1Key("ja", "en", "物語")
        assertEquals(40, a.length)
        assertEquals(a, TranslationTextPolicy.sha1Key("JA", "EN", "物語"))
        assertTrue(a != TranslationTextPolicy.sha1Key("ja", "fr", "物語"))
    }
}
