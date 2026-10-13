package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderSyncPlannerTest {
    private fun snapshot(uri: String, device: String, at: Long) =
        FolderSyncPlanner.Snapshot(uri, device, at)

    @Test fun mergesNewestFromEachOtherDeviceInTimestampOrder() {
        val candidates = listOf(
            snapshot("a-old", "a", 100), snapshot("a-new", "a", 500),
            snapshot("b-new", "b", 300), snapshot("own", "local", 700),
        )
        assertEquals(listOf("b-new", "a-new"),
            FolderSyncPlanner.pending(candidates, "local", emptyMap(), 1000).map { it.uri })
    }

    @Test fun retriesOnlySnapshotsNewerThanTheLastSuccessfulMerge() {
        val candidates = listOf(snapshot("same", "a", 500), snapshot("new", "a", 600))
        assertEquals(listOf("new"), FolderSyncPlanner.pending(
            candidates, "local", mapOf("a" to 500L), 1000).map { it.uri })
    }

    @Test fun skipsInvalidDevicesAndFutureSnapshots() {
        val day = 24L * 60 * 60 * 1000
        assertTrue(FolderSyncPlanner.pending(listOf(
            snapshot("missing", "", 1), snapshot("future", "other", day + 2000),
            snapshot("invalid", "other", 0)),
            "local", emptyMap(), 1000).isEmpty())
    }

    @Test fun syncRecognizesOnlyDedicatedSnapshotFiles() {
        assertTrue(FolderSyncPlanner.isSnapshotFileName("sync_monogatari_20261012-2300.bk.zip"))
        assertTrue(FolderSyncPlanner.isSnapshotFileName("sync_2e7e9d7b-7d63-4f1a-9825-5aa96f480d41_monogatari_20261012-2300.bk.zip"))
        assertEquals(false, FolderSyncPlanner.isSnapshotFileName("monogatari_20261012-2300.bk.zip"))
        assertEquals(false, FolderSyncPlanner.isSnapshotFileName("sync_notes.txt"))
    }
}