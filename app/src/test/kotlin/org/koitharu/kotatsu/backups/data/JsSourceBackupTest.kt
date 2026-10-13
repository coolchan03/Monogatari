package org.koitharu.kotatsu.backups.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.backups.data.model.JsSourceBackup

class JsSourceBackupTest {
    @Test fun metadataRoundTripDoesNotEmbedExecutableJavascript() {
        val original = JsSourceBackup(123L, "https://catalog.example.org/index.json", "2.4")
        val serialized = Json.encodeToString(listOf(original))
        assertEquals(listOf(original), Json.decodeFromString<List<JsSourceBackup>>(serialized))
        assertEquals(false, serialized.contains("sourceCodeUrl"))
        assertEquals(false, serialized.contains("code"))
    }
}
