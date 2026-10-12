package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.jsext.repo.DartSourceCompatibility
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.repo.SourceCodeLanguage
import org.koitharu.kotatsu.parsers.model.MangaParserSource

class DartSourceCompatibilityTest {
    private fun source(
        name: String,
        type: JsItemType = JsItemType.MANGA,
        language: SourceCodeLanguage = SourceCodeLanguage.DART,
        host: String = "https://example.org",
    ) = JsSourceEntry(
        id = name.hashCode().toLong(), name = name, baseUrl = host, lang = "en", version = "1",
        itemType = type, language = language, sourceCodeUrl = "https://example.org/src",
        iconUrl = "", apiUrl = "", dateFormat = "", dateFormatLocale = "",
        isNsfw = false, hasCloudflare = false, additionalParams = "", notes = "", repoUrl = "",
    )

    @Test fun findsExistingKotatsuParserBySiteName() {
        val result = DartSourceCompatibility.candidate(source("MangaDex"), emptyList())
        assertNotNull(result)
        assertEquals(MangaParserSource.MANGADEX, result?.parser)
        assertNull(result?.js)
    }

    @Test fun fallsBackToMatchingJavascriptSite() {
        val dart = source("Different title", type = JsItemType.ANIME, host = "https://anime.example.org")
        val js = source("Anime mirror", type = JsItemType.ANIME,
            language = SourceCodeLanguage.JAVASCRIPT, host = "https://anime.example.org")
        assertEquals(js.id, DartSourceCompatibility.candidate(dart, listOf(js))?.js?.id)
    }

    @Test fun neverClaimsUnrelatedSourceIsCompatible() {
        val dart = source("Unrelated obscure source", type = JsItemType.ANIME)
        val js = source("Another unrelated site", type = JsItemType.ANIME,
            language = SourceCodeLanguage.JAVASCRIPT, host = "https://elsewhere.org")
        assertNull(DartSourceCompatibility.candidate(dart, listOf(js)))
        assertNull(DartSourceCompatibility.candidate(js, emptyList()))
    }
}
