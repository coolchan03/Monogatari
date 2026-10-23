package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class UpdateSnapshotsTest {

	@get:Rule
	val tmp = TemporaryFolder()

	private fun setUp(): Pair<File, UpdateSnapshots> {
		val data = tmp.newFolder("data")
		File(data, "databases").mkdirs()
		File(data, "shared_prefs").mkdirs()
		File(data, "databases/kotatsu-db").writeText("v1-db")
		File(data, "databases/kotatsu-db-wal").writeText("v1-wal")
		File(data, "databases/other-db").writeText("not ours")
		File(data, "shared_prefs/settings.xml").writeText("<map>v1</map>")
		val files = File(data, "files").apply { mkdirs() }
		return data to UpdateSnapshots(data, files)
	}

	@Test
	fun freshInstallTakesNoSnapshotButRemembersVersion() {
		val (_, snapshots) = setUp()
		assertFalse(snapshots.onAppStart(10, now = 1))
		assertTrue(snapshots.list().isEmpty())
		assertFalse(snapshots.onAppStart(10, now = 2)) // same version again
		assertTrue(snapshots.list().isEmpty())
	}

	@Test
	fun updateSnapshotsDatabaseAndPrefsOnly() {
		val (_, snapshots) = setUp()
		snapshots.onAppStart(10, now = 1)
		assertTrue(snapshots.onAppStart(11, now = 2))
		val snapshot = snapshots.list().single()
		assertEquals(10L, snapshot.fromVersionCode)
		assertEquals("v1-db", File(snapshot.dir, "databases/kotatsu-db").readText())
		assertEquals("<map>v1</map>", File(snapshot.dir, "shared_prefs/settings.xml").readText())
		assertFalse(File(snapshot.dir, "databases/other-db").exists())
	}

	@Test
	fun keepsOnlyTheTwoNewestSnapshots() {
		val (_, snapshots) = setUp()
		snapshots.onAppStart(10, now = 1)
		snapshots.onAppStart(11, now = 2)
		snapshots.onAppStart(12, now = 3)
		snapshots.onAppStart(13, now = 4)
		assertEquals(listOf(12L, 11L), snapshots.list().map { it.fromVersionCode })
	}

	@Test
	fun stagedRestoreIsAppliedOnNextStartAndDropsStaleWal() {
		val (data, snapshots) = setUp()
		snapshots.onAppStart(10, now = 1)
		snapshots.onAppStart(11, now = 2)
		File(data, "databases/kotatsu-db").writeText("v2-db")
		File(data, "databases/kotatsu-db-wal").delete()
		File(data, "databases/kotatsu-db-shm").writeText("v2-shm")
		File(data, "shared_prefs/settings.xml").writeText("<map>v2</map>")
		snapshots.scheduleRestore(snapshots.list().single())
		assertTrue(snapshots.isRestorePending())
		// Nothing changes until the next start.
		assertEquals("v2-db", File(data, "databases/kotatsu-db").readText())
		snapshots.onAppStart(11, now = 3)
		assertFalse(snapshots.isRestorePending())
		assertEquals("v1-db", File(data, "databases/kotatsu-db").readText())
		assertEquals("v1-wal", File(data, "databases/kotatsu-db-wal").readText())
		assertFalse(File(data, "databases/kotatsu-db-shm").exists())
		assertEquals("<map>v1</map>", File(data, "shared_prefs/settings.xml").readText())
		assertEquals("not ours", File(data, "databases/other-db").readText())
	}

	private fun stageRestoreOverV2(): Triple<File, UpdateSnapshots, UpdateSnapshots.Snapshot> {
		val (data, snapshots) = setUp()
		snapshots.onAppStart(10, now = 1)
		snapshots.onAppStart(11, now = 2)
		File(data, "databases/kotatsu-db").writeText("v2-db")
		File(data, "databases/kotatsu-db-wal").writeText("v2-wal")
		File(data, "shared_prefs/settings.xml").writeText("<map>v2</map>")
		return Triple(data, snapshots, snapshots.list().single())
	}

	@Test
	fun snapshotWithoutDatabaseNeverTouchesLiveData() {
		val (data, snapshots, snapshot) = stageRestoreOverV2()
		File(snapshot.dir, "databases/kotatsu-db").delete()
		snapshots.scheduleRestore(snapshot)
		snapshots.onAppStart(11, now = 3)
		assertEquals("v2-db", File(data, "databases/kotatsu-db").readText())
		assertEquals("v2-wal", File(data, "databases/kotatsu-db-wal").readText())
		assertEquals("<map>v2</map>", File(data, "shared_prefs/settings.xml").readText())
	}

	@Test
	fun failedSwapKeepsOldFilesAndThePendingMarker() {
		val (data, snapshots, snapshot) = stageRestoreOverV2()
		// A non-empty directory where a preferences file belongs makes the final rename fail.
		val prefs = File(data, "shared_prefs/settings.xml")
		prefs.delete()
		File(prefs, "blocker").apply { parentFile.mkdirs() }.writeText("x")
		snapshots.scheduleRestore(snapshot)
		snapshots.onAppStart(11, now = 3)
		assertTrue(snapshots.isRestorePending())
		assertEquals("v2-db", File(data, "databases/kotatsu-db").readText())
		assertEquals("v2-wal", File(data, "databases/kotatsu-db-wal").readText())
		assertTrue(File(data, "databases").listFiles().orEmpty().none { it.name.endsWith(".restoring") })
	}

	@Test
	fun interruptedSwapIsRolledBackOnNextStart() {
		val (data, snapshots, snapshot) = stageRestoreOverV2()
		// Simulate a crash after the old files were set aside and the database was half replaced.
		val backup = File(data, "files/update_snapshots/restore_backup/databases").apply { mkdirs() }
		File(backup, "kotatsu-db").writeText("v2-db")
		File(data, "databases/kotatsu-db").writeText("half-written")
		File(data, "databases/kotatsu-db.restoring").writeText("partial")
		snapshots.scheduleRestore(snapshot)
		snapshots.onAppStart(11, now = 3)
		// Rolled back, then the restore was applied cleanly.
		assertFalse(snapshots.isRestorePending())
		assertEquals("v1-db", File(data, "databases/kotatsu-db").readText())
		assertFalse(File(data, "databases/kotatsu-db.restoring").exists())
		assertFalse(File(data, "files/update_snapshots/restore_backup").exists())
	}
}
