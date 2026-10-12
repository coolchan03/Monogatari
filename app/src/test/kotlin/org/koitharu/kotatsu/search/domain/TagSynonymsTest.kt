package org.koitharu.kotatsu.search.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagSynonymsTest {
    @Test fun blAndGlVariants() {
        assertTrue(TagSynonyms.matches("yaoi", "Boys' Love"))
        assertTrue(TagSynonyms.matches("m/m", "BL"))
        assertTrue(TagSynonyms.matches("danmei", "Shounen Ai"))
        assertTrue(TagSynonyms.matches("gl", "Girls' Love"))
        assertTrue(TagSynonyms.matches("yuri", "F/F"))
    }

    @Test fun unrelatedTagsAreNotEqual() {
        assertTrue(TagSynonyms.matches("action", "action"))
        assertFalse(TagSynonyms.matches("action", "romance"))
        assertFalse(TagSynonyms.matches("yaoi", "action"))
        assertFalse(TagSynonyms.matches("gl", "bl"))
    }
}
