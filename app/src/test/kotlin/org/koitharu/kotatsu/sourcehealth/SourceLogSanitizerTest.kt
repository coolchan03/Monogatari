package org.koitharu.kotatsu.sourcehealth

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLogSanitizerTest {
    @Test fun redactsHeadersUrlUserInfoAndQuerySecrets() {
        val examples = listOf(
            "Bearer abcdefgh1234567890", "Authorization: Basic YWxhZGRpbjpvcGVuc2VzYW1l", "Authorization: BasicAbcdXYZ1234",
            "https://username:p%40ss@books.example.org/opds",
            "https://plex.example.org/library?X-Plex-Token=private123&limit=5",
            "https://example.org/?api_key=secretvalue&hello=1",
        )
        examples.forEach {
            val redacted = SourceLogSanitizer.redact(it)
            assertTrue(redacted, "[redacted]" in redacted)
            assertFalse(redacted, "secretvalue" in redacted)
            assertFalse(redacted, "private123" in redacted)
            assertFalse(redacted, "p%40ss" in redacted)
            assertFalse(redacted, "YWxhZGRpbjpvcGVuc2VzYW1l" in redacted)
        }
    }

    @Test fun preservesNonSensitiveFailureDetails() {
        val line = "HTTP 429: Retry-After 30, /books search failed"
        assertTrue(SourceLogSanitizer.redact(line) == line)
    }
}
