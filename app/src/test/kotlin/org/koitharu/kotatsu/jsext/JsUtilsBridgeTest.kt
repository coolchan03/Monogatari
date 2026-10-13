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
        assertEquals(Unit, bridge.handle("evaluateJavascriptViaWebview", JsonArray(emptyList())))
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

    @Test fun parseDatesAnswersJsonArrayOfMillis() {
        val json = bridge.handle("parseDates", JsonArray(listOf(
            JsonArray(listOf(JsonPrimitive("1700000000"), JsonPrimitive(""))),
            JsonPrimitive(""), JsonPrimitive("en"),
        ))) as String
        assertEquals("[\"1700000000000\"]", json)
    }

    @Test fun unpackAndDeobfuscateAreNoLongerUnsupported() {
        assertTrue("unpackJs" !in JsUtilsBridge.UNSUPPORTED)
        assertEquals("", bridge.handle("unpackJs", JsonArray(listOf(JsonPrimitive("plain")))))
        assertEquals("1", bridge.handle("deobfuscateJsPassword", JsonArray(listOf(JsonPrimitive("[+!+[]]")))))
    }
}
