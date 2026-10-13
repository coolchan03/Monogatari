package org.koitharu.kotatsu.sourcehealth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class SourceWebsiteGroupsTest {
    @Test fun canonicalizesOnlyKnownWebHostPrefixes() {
        assertEquals("mangadex.org", SourceWebsiteGroups.host("https://m.mangadex.org/path"))
        assertEquals("mangadex.org", SourceWebsiteGroups.host("https://WWW.MANGADEX.ORG"))
        assertNull(SourceWebsiteGroups.host("file:///private/book"))
        assertNull(SourceWebsiteGroups.host("ftp://files.example.org/data"))
        assertEquals("mangadex.org", SourceWebsiteGroups.host("  m.WWW.M.MANGADEX.ORG. "))
        assertEquals("mangadex.org", SourceWebsiteGroups.host("WWW.MANGADEX.ORG"))
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

    @Test fun parserDomainIsResolvedOnlyWhenAvailable() {
        val parser = MangaParserSource.entries.first()
        val expected = "example.org|${parser.mediaType().name}"
        assertEquals(expected, SourceWebsiteGroups.key(parser) { "www.m.example.org" })
        assertNull(SourceWebsiteGroups.key(parser) { null })
        assertNull(SourceWebsiteGroups.key(parser) { error("unsupported parser") })
    }

    @Test fun parserDomainsGroupWithoutHidingUnknownParsers() {
        val first = MangaParserSource.entries.first()
        val second = MangaParserSource.entries.first { it != first && it.mediaType() == first.mediaType() }
        val grouped = SourceWebsiteGroups.groups(listOf(first, second)) { "m.www.example.org" }
        assertEquals(listOf(listOf(first, second)), grouped)
        val unknown = SourceWebsiteGroups.groups(listOf(first, second)) { null }
        assertEquals(listOf(listOf(first), listOf(second)), unknown)
    }

    @Test fun archiveKindsDoNotHideOneAnother() {
        val items = listOf(BuiltinSource.IA_BOOKS, BuiltinSource.IA_VIDEO, BuiltinSource.IA_AUDIO)
        assertEquals(3, SourceWebsiteGroups.groups(items).size)
    }
}
