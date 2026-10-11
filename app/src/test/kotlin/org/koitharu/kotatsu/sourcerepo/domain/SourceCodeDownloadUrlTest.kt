package org.koitharu.kotatsu.sourcerepo.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.repo.SourceCodeLanguage

class SourceCodeDownloadUrlTest {
    private fun entry(url: String, lang: String = "en") = JsSourceEntry(
        id = 1004796349L, name = "Animeonsen", baseUrl = "", lang = lang, version = "1",
        itemType = JsItemType.ANIME, language = SourceCodeLanguage.JAVASCRIPT,
        sourceCodeUrl = url, iconUrl = "", apiUrl = "", dateFormat = "",
        dateFormatLocale = "", isNsfw = false, hasCloudflare = false,
        additionalParams = "", notes = "", repoUrl = "",
    )

    @Test fun repairsMalformedDirectoryUrl() {
        val dir = "https://raw.githubusercontent.com/Swakshan/mangayomi-swak-extensions/refs/heads/main/javascript/anime/src/"
        assertEquals(dir + "en/animeonsen.js", sourceCodeDownloadUrl(entry(dir)))
    }

    @Test fun preservesNormalScriptAndUnsafeOrUnknownPaths() {
        val dir = "https://raw.githubusercontent.com/other/repo/main/javascript/anime/src/"
        assertEquals(dir + "en/animeonsen.js", sourceCodeDownloadUrl(entry(dir)))
        assertEquals(dir + "en/example.js", sourceCodeDownloadUrl(entry(dir + "en/example.js")))
        assertEquals("https://example.com/src/", sourceCodeDownloadUrl(entry("https://example.com/src/")))
        assertEquals(dir, sourceCodeDownloadUrl(entry(dir, lang = "../en")))
    }
}
