package org.koitharu.kotatsu.backups.data

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.backups.data.model.BackupIndex
import org.koitharu.kotatsu.backups.data.model.CategoryBackup

class BackupCompatibilityTest {
    @Test fun oldBackupIndexWithoutDeviceFieldsStillOpens() {
        val restored = Json.decodeFromString<BackupIndex>(
            """{"app_id":"org.test.monogatari","app_version":4,"created_at":12345}"""
        )
        assertEquals("", restored.deviceId)
        assertEquals("", restored.deviceName)
    }

    @Test fun oldCategoryWithoutTombstoneStillOpens() {
        val restored = Json.decodeFromString<CategoryBackup>(
            """{"category_id":5,"created_at":123,"sort_key":1,"title":"Favorites"}"""
        )
        assertEquals(0L, restored.deletedAt)
    }
}
