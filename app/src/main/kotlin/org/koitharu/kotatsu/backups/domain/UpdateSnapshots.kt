package org.koitharu.kotatsu.backups.domain

import java.io.File
import java.io.IOException

/**
 * Keeps user data safe across app updates (roadmap #88).
 *
 * The first time a new app version starts, before anything opens the database, the library database and all
 * preference files are copied to `files/update_snapshots/<old version>_<time>/`. If an update breaks or loses
 * something, Settings > Backup offers to roll back to that copy. Rolling back is staged: it only records which
 * snapshot to use, and the copy happens on the next start, again before the database is opened, so a live
 * SQLite file is never overwritten.
 *
 * Plain java.io only: this runs from `Application.attachBaseContext`, before dependency injection exists.
 */
class UpdateSnapshots(private val dataDir: File, private val filesDir: File) {

	class Snapshot(val dir: File, val fromVersionCode: Long, val createdAt: Long, val sizeBytes: Long)

	private val root = File(filesDir, ROOT)
	private val stateFile = File(root, STATE_FILE)
	private val pendingFile = File(root, PENDING_FILE)

	/**
	 * Call once per process start, before the database is opened. Applies a staged roll-back, then snapshots
	 * the current data if this is the first start of a different version. Never throws.
	 */
	fun onAppStart(currentVersionCode: Long, now: Long = System.currentTimeMillis()): Boolean = try {
		root.mkdirs()
		applyPendingRestore()
		val previous = stateFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
		var snapshotTaken = false
		if (previous != null && previous != currentVersionCode && hasData()) {
			snapshotTaken = snapshot(previous, now) != null
		}
		if (previous != currentVersionCode) stateFile.writeText(currentVersionCode.toString())
		snapshotTaken
	} catch (e: Exception) {
		false
	}

	fun list(): List<Snapshot> = root.listFiles { f -> f.isDirectory && SNAPSHOT_NAME.matches(f.name) }
		.orEmpty()
		.mapNotNull { dir ->
			val (version, time) = SNAPSHOT_NAME.matchEntire(dir.name)?.destructured ?: return@mapNotNull null
			Snapshot(dir, version.toLong(), time.toLong(), dir.walkTopDown().filter { it.isFile }.sumOf { it.length() })
		}
		.sortedByDescending { it.createdAt }

	/** Stages [snapshot] to replace the current data on the next app start. */
	fun scheduleRestore(snapshot: Snapshot) {
		require(snapshot.dir.parentFile == root) { "Not an update snapshot" }
		pendingFile.writeText(snapshot.dir.name)
	}

	fun isRestorePending(): Boolean = pendingFile.isFile

	fun cancelPendingRestore() {
		pendingFile.delete()
	}

	internal fun snapshot(fromVersion: Long, now: Long): File? {
		val sources = dataFiles()
		val total = sources.sumOf { it.length() }
		if (total == 0L || total > MAX_SNAPSHOT_BYTES) return null
		if (filesDir.usableSpace in 0 until total * 2) return null // keep room for the app itself
		val target = File(root, "${fromVersion}_$now")
		val temp = File(root, "${target.name}.tmp")
		temp.deleteRecursively()
		try {
			for (file in sources) {
				val relative = file.relativeTo(dataDir).path
				file.copyTo(File(temp, relative), overwrite = true)
			}
			if (!temp.renameTo(target)) throw IOException("Could not finish snapshot")
		} catch (e: Exception) {
			temp.deleteRecursively()
			return null
		}
		prune()
		return target
	}

	/**
	 * Replaces the live database and preferences with a snapshot, never leaving the user without a working copy:
	 * the snapshot must contain the database, its files are first copied next to the live ones under temporary
	 * names and size-checked, the old files are kept in `restore_backup` until every rename has succeeded, and any
	 * failure (or a crash, found on the next start) puts the old files back. The pending marker is only cleared once
	 * the swap is complete, or when the snapshot can never be used and nothing was touched.
	 */
	private fun applyPendingRestore() {
		if (!pendingFile.isFile) return
		recoverInterruptedSwap()
		val name = pendingFile.readText().trim()
		val snapshot = File(root, name).takeIf { SNAPSHOT_NAME.matches(name) && it.isDirectory }
		val snapshotDb = snapshot?.let { File(it, "$DB_DIR/$DB_NAME") }
		if (snapshot == null || snapshotDb == null || !snapshotDb.isFile || snapshotDb.length() == 0L) {
			pendingFile.delete() // unusable and nothing was changed, so don't keep retrying it
			return
		}
		try {
			swapIn(snapshot)
			pendingFile.delete()
		} catch (e: Exception) {
			// Live data is back as it was (or untouched); keep the marker so a later start can retry.
		}
	}

	private fun swapIn(snapshot: File) {
		val incoming = snapshot.walkTopDown().filter { it.isFile }
			.map { it.relativeTo(snapshot).path }
			.filter { it.startsWith("$DB_DIR/") || it.startsWith("$PREFS_DIR/") }
			.toList()
		deleteTemps()
		try {
			for (relative in incoming) {
				val source = File(snapshot, relative)
				val temp = File(dataDir, relative + TEMP_SUFFIX)
				source.copyTo(temp, overwrite = true)
				if (temp.length() != source.length()) throw IOException("Incomplete copy of $relative")
			}
			// Keep the files about to be replaced or removed until the swap has succeeded.
			val backupTemp = File(root, "$BACKUP_DIR.tmp")
			backupTemp.deleteRecursively()
			val replaced = liveDbFiles() + incoming.map { File(dataDir, it) }.filter { it.isFile }
			for (file in replaced.distinct()) {
				file.copyTo(File(backupTemp, file.relativeTo(dataDir).path), overwrite = true)
			}
			backupTemp.mkdirs()
			if (!backupTemp.renameTo(File(root, BACKUP_DIR))) throw IOException("Could not keep the current data")
		} catch (e: Exception) {
			deleteTemps()
			File(root, "$BACKUP_DIR.tmp").deleteRecursively()
			throw e
		}
		try {
			// Remove current database files first so stale -wal/-shm files can't be replayed onto the new database.
			liveDbFiles().filter { it.relativeTo(dataDir).path !in incoming }.forEach { it.delete() }
			for (relative in incoming) {
				val temp = File(dataDir, relative + TEMP_SUFFIX)
				if (!temp.renameTo(File(dataDir, relative))) throw IOException("Could not replace $relative")
			}
		} catch (e: Exception) {
			rollback()
			File(root, BACKUP_DIR).deleteRecursively()
			throw e
		}
		File(root, BACKUP_DIR).deleteRecursively()
	}

	/** A complete `restore_backup` folder means an earlier swap did not finish: put its files back first. */
	private fun recoverInterruptedSwap() {
		val backup = File(root, BACKUP_DIR)
		File(root, "$BACKUP_DIR.tmp").deleteRecursively()
		deleteTemps()
		if (backup.isDirectory) {
			rollback()
			backup.deleteRecursively()
		}
	}

	private fun rollback() {
		val backup = File(root, BACKUP_DIR)
		deleteTemps()
		if (!backup.isDirectory) return
		liveDbFiles().forEach { it.delete() }
		backup.walkTopDown().filter { it.isFile }.forEach { file ->
			file.copyTo(File(dataDir, file.relativeTo(backup).path), overwrite = true)
		}
	}

	private fun deleteTemps() {
		for (dir in listOf(File(dataDir, DB_DIR), File(dataDir, PREFS_DIR))) {
			dir.listFiles { f -> f.name.endsWith(TEMP_SUFFIX) }?.forEach { it.delete() }
		}
	}

	private fun liveDbFiles(): List<File> = File(dataDir, DB_DIR)
		.listFiles { f -> f.isFile && f.name.startsWith(DB_NAME) && !f.name.endsWith(TEMP_SUFFIX) }
		.orEmpty().toList()

	private fun prune() {
		list().drop(KEEP).forEach { it.dir.deleteRecursively() }
		root.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.deleteRecursively() }
	}

	private fun hasData(): Boolean = File(File(dataDir, DB_DIR), DB_NAME).isFile

	private fun dataFiles(): List<File> {
		val db = liveDbFiles()
		val prefs = File(dataDir, PREFS_DIR).listFiles { f -> f.isFile && f.name.endsWith(".xml") }.orEmpty().toList()
		return db + prefs
	}

	companion object {

		const val DB_NAME = "kotatsu-db"
		private const val DB_DIR = "databases"
		private const val PREFS_DIR = "shared_prefs"
		private const val ROOT = "update_snapshots"
		private const val STATE_FILE = "last_version"
		private const val PENDING_FILE = "restore_pending"
		private const val BACKUP_DIR = "restore_backup"
		private const val TEMP_SUFFIX = ".restoring"
		private const val KEEP = 2
		private const val MAX_SNAPSHOT_BYTES = 400L * 1024 * 1024
		private val SNAPSHOT_NAME = Regex("""(\d+)_(\d+)""")
	}
}
