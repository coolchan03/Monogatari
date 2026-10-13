package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MissingJsHostDiagnosticsTest {
    @Test fun countsNamesButNotPrivateData() {
        MissingJsHostDiagnostics.clear()
        MissingJsHostDiagnostics.record("parseDates")
        MissingJsHostDiagnostics.record("parseDates")
        MissingJsHostDiagnostics.record("https://secret.example/?token=ABC")
        assertEquals(2, MissingJsHostDiagnostics.snapshot()["parseDates"])
        assertFalse(MissingJsHostDiagnostics.snapshot().keys.any { "secret" in it })
        MissingJsHostDiagnostics.clear()
    }
}
