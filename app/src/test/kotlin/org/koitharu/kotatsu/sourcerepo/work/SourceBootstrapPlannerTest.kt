package org.koitharu.kotatsu.sourcerepo.work

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.repo.SourceCodeLanguage

class SourceBootstrapPlannerTest {
    private fun source(
        id: Long, site: String, lang: String = "en", adult: Boolean = false,
        version: String = "1.0", type: JsItemType = JsItemType.MANGA,
        language: SourceCodeLanguage = SourceCodeLanguage.JAVASCRIPT,
    ) = JsSourceEntry(
        id, "Site $id", site, lang, version, type, language,
        "https://example.net/code.js", "", "", "", "", adult,
        false, "", "", "https://example.net/index.json",
    )

    @Test fun hostNormalizesMobileAndWww() {
        assertEquals("mangadex.org", SourceBootstrapPlanner.normalizedHost("https://m.mangadex.org/title"))
        assertEquals("mangadex.org", SourceBootstrapPlanner.normalizedHost("https://WWW.MANGADEX.ORG"))
        assertEquals(null, SourceBootstrapPlanner.normalizedHost("javascript:alert(1)"))
    }

    @Test fun languageNsfwAndExecutionFiltersApply() {
        val candidates = listOf(
            source(1, "https://books.test", "en"),
            source(2, "https://novels.test", "all", type = JsItemType.NOVEL),
            source(3, "https://spanish.test", "es"),
            source(4, "https://adult.test", "en", adult = true),
            source(5, "https://dart.test", language = SourceCodeLanguage.DART),
        )
        val selected = SourceBootstrapPlanner.plan(candidates, emptyList(), emptySet(), false)
        assertEquals(setOf(1L, 2L), selected.map { it.id }.toSet())
        val allowed = SourceBootstrapPlanner.plan(candidates, emptyList(), emptySet(), true)
        assertEquals(setOf(1L, 2L, 4L), allowed.map { it.id }.toSet())
    }

    @Test fun installedParserAndDuplicateHostAreAvoided() {
        val candidates = listOf(
            source(1, "https://www.mangadex.org"),
            source(2, "https://another.test"),
            source(3, "https://m.another.test", version = "2.0"),
            source(4, "https://already.test"),
        )
        val selected = SourceBootstrapPlanner.plan(
            candidates, listOf(source(999, "https://already.test")), setOf("MANGADEX"), true,
        )
        assertEquals(listOf(3L), selected.map { it.id })
    }

    @Test fun differentMediaCategoriesAtSameSiteRemainAvailable() {
        val candidates = listOf(
            source(10, "https://one.test", type = JsItemType.MANGA),
            source(11, "https://one.test", type = JsItemType.NOVEL),
        )
        val selected = SourceBootstrapPlanner.plan(candidates, emptyList(), emptySet(), true)
        assertEquals(2, selected.size)
        assertTrue(selected.map { it.id }.containsAll(listOf(10L, 11L)))
    }
}
