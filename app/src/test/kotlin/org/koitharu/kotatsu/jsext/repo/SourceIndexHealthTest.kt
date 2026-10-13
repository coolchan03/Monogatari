package org.koitharu.kotatsu.jsext.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class SourceIndexHealthTest {
    private val now = 1_800_000_000_000L
    private fun date(at: Long) = Instant.ofEpochMilli(at).toString()

    @Test fun confirmedRecentBreakIsHidden() {
        assertTrue(SourceIndexHealth.shouldHide("broken", date(now - 60_000L), now))
        assertTrue(SourceIndexHealth.shouldHide("BROKEN", date(now - 60_000L), now))
    }

    @Test fun unknownOrStaleResultsNeverHideSources() {
        assertFalse(SourceIndexHealth.shouldHide("unverified", date(now), now))
        assertFalse(SourceIndexHealth.shouldHide("ok", date(now), now))
        assertFalse(SourceIndexHealth.shouldHide("broken", null, now))
        assertFalse(SourceIndexHealth.shouldHide("broken", "invalid", now))
        assertFalse(SourceIndexHealth.shouldHide("broken", date(now - 8L * 86_400_000), now))
        assertFalse(SourceIndexHealth.shouldHide("broken", date(now + 10L * 60_000), now))
    }

    @Test fun mangayomiParserFiltersOnlyRecentConfirmedBroken() {
        val checkedAt = Instant.now().toString()
        fun index(status: String, timestamp: String) = """
            [{"id":123,"name":"Example","sourceCodeUrl":"https://example.org/site.js",
            "baseUrl":"https://example.org","lang":"en","sourceCodeLanguage":1,
            "status":"$status","checkedAt":"$timestamp"}]
        """.trimIndent()
        val repo = "https://example.org/index.json"
        assertEquals(0, MangayomiIndexParser.parse(index("broken", checkedAt), repo).size)
        assertEquals(1, MangayomiIndexParser.parse(index("unverified", checkedAt), repo).size)
        assertEquals(1, MangayomiIndexParser.parse(index("broken", date(now - 60L * 86_400_000)), repo).size)
    }
}
