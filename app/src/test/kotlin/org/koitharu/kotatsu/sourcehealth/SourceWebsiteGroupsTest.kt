package org.koitharu.kotatsu.sourcehealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource

class SourceWebsiteGroupsTest {
    @Test fun canonicalizesOnlyKnownWebHostPrefixes() {
        assertEquals("mangadex.org", SourceWebsiteGroups.host("https://m.mangadex.org/path"))
        assertEquals("mangadex.org", SourceWebsiteGroups.host("https://WWW.MANGADEX.ORG"))
        assertNull(SourceWebsiteGroups.host("file:///private/book"))
    }

    @Test fun preservesFirstChoiceAndFallbackOrder() {
        val groups = SourceWebsiteGroups.orderedGroups(listOf("a1", "b1", "a2", "other", "b2")) {
            when {
                it.startsWith("a") -> "a.org|MANGA"
                it.startsWith("b") -> "b.org|MANGA"
                else -> null
            }
        }
        assertEquals(listOf(listOf("a1", "a2"), listOf("b1", "b2"), listOf("other")), groups)
    }

    @Test fun archiveKindsDoNotHideOneAnother() {
        val items = listOf(BuiltinSource.IA_BOOKS, BuiltinSource.IA_VIDEO, BuiltinSource.IA_AUDIO)
        assertEquals(3, SourceWebsiteGroups.groups(items).size)
    }
}
