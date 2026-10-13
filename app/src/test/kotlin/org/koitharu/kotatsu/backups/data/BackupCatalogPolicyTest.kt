package org.koitharu.kotatsu.backups.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCatalogPolicyTest {
    @Test fun normalHttpsCatalogUrlIsPreserved() {
        assertEquals("https://catalog.example.org/opds",
            BackupCatalogPolicy.safeUrl("https://catalog.example.org/opds", true))
        assertEquals("http://catalog.example.org/books",
            BackupCatalogPolicy.safeUrl("http://catalog.example.org/books", false))
        assertNull(BackupCatalogPolicy.safeUrl("http://catalog.example.org/books", true))
    }

    @Test fun rejectsEmbeddedCredentialsAndSecretQueries() {
        assertNull(BackupCatalogPolicy.safeUrl("https://person:pass@catalog.example.org/books", false))
        assertNull(BackupCatalogPolicy.safeUrl("https://catalog.example.org/opds?access_token=secret", true))
        assertNull(BackupCatalogPolicy.safeUrl("https://catalog.example.org/feed?api_key=x", true))
        assertNull(BackupCatalogPolicy.safeUrl("https://catalog.example.org/feed?access%5Ftoken=x", true))
        assertNull(BackupCatalogPolicy.safeUrl("https://catalog.example.org/feed?access%255Ftoken=x", true))
        assertNull(BackupCatalogPolicy.safeUrl("https://catalog.example.org/?q=books#mysecret", false))
    }

    @Test fun blocksSecretsInGeneralSettingsBackup() {
        assertTrue(BackupCatalogPolicy.safePreferenceKey("theme"))
        assertTrue(BackupCatalogPolicy.safePreferenceKey("sort_order"))
        assertFalse(BackupCatalogPolicy.safePreferenceKey("discord_token"))
        assertFalse(BackupCatalogPolicy.safePreferenceKey("proxy_password"))
        assertFalse(BackupCatalogPolicy.safePreferenceKey("cf_clearance_cookie"))
        assertFalse(BackupCatalogPolicy.safePreferenceKey("backup_folder_sync"))
    }
}
