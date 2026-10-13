package org.koitharu.kotatsu.jsext

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsUtilsBridgeTest {
    private val bridge = JsUtilsBridge(JsLogger { })

    @Test fun unsupportedCallIsReportedOnceByDispatcherNotUtility() {
        MissingJsHostDiagnostics.clear()
        assertEquals(Unit, bridge.handle("parseDates", JsonArray(emptyList())))
        assertTrue(MissingJsHostDiagnostics.snapshot().isEmpty())
    }

    @Test fun upstreamAesCbcRoundTripStillWorks() {
        val key = "0123456789abcdef"
        val iv = "fedcba9876543210"
        val encrypted = bridge.handle("cryptoHandler", JsonArray(listOf(
            JsonPrimitive("Monogatari"), JsonPrimitive(iv),
            JsonPrimitive(key), JsonPrimitive(true),
        ))) as String
        val plain = bridge.handle("cryptoHandler", JsonArray(listOf(
            JsonPrimitive(encrypted), JsonPrimitive(iv),
            JsonPrimitive(key), JsonPrimitive(false),
        ))) as String
        assertEquals("Monogatari", plain)
    }
}
