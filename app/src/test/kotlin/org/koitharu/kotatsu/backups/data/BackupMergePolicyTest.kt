package org.koitharu.kotatsu.backups.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupMergePolicyTest {
    @Test fun olderBackupCannotRewindReadingPosition() {
        assertFalse(BackupMergePolicy.shouldReplace(200L, 0L, 150L, 0L))
        assertTrue(BackupMergePolicy.shouldReplace(150L, 0L, 200L, 0L))
        assertFalse(BackupMergePolicy.shouldReplace(200L, 0L, 200L, 0L))
    }

    @Test fun deletedEntryCannotBeResurrectedByOlderBackup() {
        assertFalse(BackupMergePolicy.shouldReplace(120L, 300L, 250L, 0L))
        assertTrue(BackupMergePolicy.shouldReplace(120L, 300L, 301L, 0L))
    }

    @Test fun latestDeletionWinsIncludingTimestampTies() {
        assertTrue(BackupMergePolicy.shouldReplace(200L, 0L, 140L, 250L))
        assertTrue(BackupMergePolicy.shouldReplace(200L, 0L, 140L, 200L))
        assertFalse(BackupMergePolicy.shouldReplace(200L, 200L, 200L, 0L))
    }

    @Test fun acceptsNewRecordsAndPreservesExistingEqualVersions() {
        assertTrue(BackupMergePolicy.shouldReplace(null, 0L, 100L, 0L))
        assertFalse(BackupMergePolicy.shouldReplace(100L, 0L, 100L, 0L))
    }
}
