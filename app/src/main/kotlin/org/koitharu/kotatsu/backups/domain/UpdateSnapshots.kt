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

	private fun applyPendingRestore() {
		if (!pendingFile.isFile) return
		val name = pendingFile.readText().trim()
		pendingFile.delete() // never loop on a broken snapshot
		val snapshot = File(root, name).takeIf { SNAPSHOT_NAME.matches(name) && it.isDirectory } ?: return
		// Remove current database files first so stale -wal/-shm files can't be replayed onto the old database.
		File(dataDir, DB_DIR).listFiles { f -> f.name.startsWith(DB_NAME) }?.forEach { it.delete() }
		snapshot.walkTopDown().filter { it.isFile }.forEach { file ->
			val relative = file.relativeTo(snapshot).path
			if (relative.startsWith(DB_DIR) || relative.startsWith(PREFS_DIR)) {
				file.copyTo(File(dataDir, relative), overwrite = true)
			}
		}
	}

	private fun prune() {
		list().drop(KEEP).forEach { it.dir.deleteRecursively() }
		root.listFiles { f -> f.name.endsWith(".tmp") }?.forEach { it.deleteRecursively() }
	}

	private fun hasData(): Boolean = File(File(dataDir, DB_DIR), DB_NAME).isFile

	private fun dataFiles(): List<File> {
		val db = File(dataDir, DB_DIR).listFiles { f -> f.isFile && f.name.startsWith(DB_NAME) }.orEmpty().toList()
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
		private const val KEEP = 2
		private const val MAX_SNAPSHOT_BYTES = 400L * 1024 * 1024
		private val SNAPSHOT_NAME = Regex("""(\d+)_(\d+)""")
	}
}
