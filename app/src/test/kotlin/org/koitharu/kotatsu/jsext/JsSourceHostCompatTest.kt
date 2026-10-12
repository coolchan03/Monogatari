package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.jsext.source.compatibleJsSourceUrl

class JsSourceHostCompatTest {
    @Test fun mapsOnlyRetiredComickApiHost() {
        assertEquals(
            "https://api.comick.dev/v1.0/search?q=love",
            compatibleJsSourceUrl("https://api.comick.fun/v1.0/search?q=love"),
        )
    }

    @Test fun leavesUnrelatedUrlsAndInsecureSchemesUntouched() {
        val examples = listOf(
            "https://example.org/?next=https://api.comick.fun/",
            "https://api.comick.fun.evil.invalid/v1.0/search",
            "http://api.comick.fun/v1.0/search",
            "https://api.comick.dev/v1.0/search",
            "https://comick.fun/",
        )
        examples.forEach { url -> assertEquals(url, compatibleJsSourceUrl(url)) }
    }
}
