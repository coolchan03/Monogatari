package org.koitharu.kotatsu.jsext.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class JsSourceDisclosureTest {
    private val index = """[{
      "id":887244,
      "name":"Nightly Reader",
      "baseUrl":"https://fiction.example/",
      "lang":"ja",
      "version":"1.2.3",
      "itemType":1,
      "sourceCodeLanguage":1,
      "sourceCodeUrl":"https://cdn.example/reader.js",
      "repoUrl":"https://git.example/manga/index.json",
      "status":"updating",
      "checkedAt":"2026-10-15T10:00:00Z",
      "author":"Community Authors",
      "license":"MIT",
      "permissions":["network","cookies","network"],
      "hasCloudflare":true
    }]"""

    @Test fun indexMetadataIsVisibleBeforeInstalling() {
        val entry = MangayomiIndexParser.parse(index, "https://git.example/manga/index.json").single()
        assertEquals("updating", entry.indexStatus)
        assertEquals("Community Authors", entry.author)
        assertEquals("MIT", entry.license)
        assertEquals(listOf("network", "cookies", "network"), entry.declaredPermissions)
        assertEquals("Index: updating", JsSourceDisclosure.indexState(entry))
        val details = JsSourceDisclosure.details(entry)
        assertTrue(details.contains("Source repository: git.example"))
        assertTrue(details.contains("Declared permissions: network, cookies"))
        assertTrue(details.contains("Publisher/author: Community Authors"))
        assertTrue(details.contains("not an independent security check"))
    }

    @Test fun disclosuresPersistAcrossInstalledSourceRestarts() {
        val entry = MangayomiIndexParser.parse(index, "https://git.example/manga/index.json").single()
        val folder = Files.createTempDirectory("monogatari-js-source-").toFile()
        try {
            val store = JsSourceStore(folder)
            store.install(entry, "source = {}")
            val stored = JsSourceStore(folder).get(entry.id)!!.entry
            assertEquals(entry.indexStatus, stored.indexStatus)
            assertEquals(entry.author, stored.author)
            assertEquals(entry.license, stored.license)
            assertEquals(entry.declaredPermissions, stored.declaredPermissions)
        } finally {
            folder.deleteRecursively()
        }
    }

    @Test fun unverifiedPermissionMetadataIsNotMisrepresentedAsGranted() {
        val entry = MangayomiIndexParser.parse(index, "https://git.example/manga/index.json").single()
            .copy(indexStatus = "broken", indexCheckedAt = null, declaredPermissions = emptyList())
        val details = JsSourceDisclosure.details(entry)
        assertTrue(details.contains("outdated failure; retest"))
        assertTrue(details.contains("Declared permissions: not supplied by repository"))
        assertFalse(details.contains("Permissions granted"))
    }
}
