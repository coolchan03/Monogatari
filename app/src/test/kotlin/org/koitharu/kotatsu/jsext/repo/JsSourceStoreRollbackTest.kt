package org.koitharu.kotatsu.jsext.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.jsext.JsItemType
import java.nio.file.Files
import java.io.File

class JsSourceStoreRollbackTest {
    private fun entry(version: String) = JsSourceEntry(
        id = 123, name = "Example", baseUrl = "https://example.org",
        lang = "en", version = version, itemType = JsItemType.fromCode(null),
        language = SourceCodeLanguage.JAVASCRIPT, sourceCodeUrl = "https://example.org/source.js",
        iconUrl = "", apiUrl = "", dateFormat = "", dateFormatLocale = "",
        isNsfw = false, hasCloudflare = false, additionalParams = "", notes = "",
        repoUrl = "https://example.org/index.json",
    )

    @Test fun installsAndRestoresPreviousVersionWithPause() {
        val dir = Files.createTempDirectory("js-source-rollback").toFile()
        try {
            val store = JsSourceStore(dir)
            store.install(entry("1.0"), "class DefaultExtension { version = 1 }")
            store.install(entry("2.0"), "class DefaultExtension { version = 2 }")
            assertTrue(store.hasPrevious(123))
            assertEquals("2.0", store.get(123)?.entry?.version)
            assertTrue(store.rollback(123, now = 1000))
            assertEquals("1.0", store.get(123)?.entry?.version)
            assertEquals("class DefaultExtension { version = 1 }", store.get(123)?.code)
            assertTrue(store.isUpdatePaused(123, now = 1001))
            assertFalse(store.isUpdatePaused(123, now = 1000 + 49L * 60 * 60 * 1000))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test fun interruptedReplacementRecoversWithoutLosingInstalledSource() {
        val dir = Files.createTempDirectory("js-source-recovery").toFile()
        try {
            val store = JsSourceStore(dir)
            store.install(entry("1.0"), "class DefaultExtension { version = 1 }")
            store.install(entry("2.0"), "class DefaultExtension { version = 2 }")
            File(dir, "123.updating").writeText("pending")
            File(dir, "123.js").writeText("partial new source")
            val restored = JsSourceStore(dir)
            assertEquals("1.0", restored.get(123)?.entry?.version)
            assertEquals("class DefaultExtension { version = 1 }", restored.get(123)?.code)
            assertFalse(File(dir, "123.updating").exists())
        } finally {
            dir.deleteRecursively()
        }
    }
}
