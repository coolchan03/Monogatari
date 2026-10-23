package org.koitharu.kotatsu.backups.domain

import java.io.File
import java.io.IOException

/**
 * Preserves user data around app upgrades and applies user-requested restores before Room opens.
 * All replacement bytes and a recoverable copy of existing files are prepared before any live file changes.
 */
class UpdateSnapshots(private val dataDir: File, private val filesDir: File) {

	class Snapshot(val dir: File, val fromVersionCode: Long, val createdAt: Long, val sizeBytes: Long)

	private val root = File(filesDir, ROOT)
	private val stateFile = File(root, STATE_FILE)
	private val pendingFile = File(root, PENDING_FILE)
	private val transaction = File(root, RESTORE_TRANSACTION)
	private val rollbackReady = File(transaction, ROLLBACK_READY)

	/**
	 * Called before Room or SharedPreferences are opened. If a previous restore was interrupted,
	 * first recover the pre-restore files instead of attempting a second partial restore.
	 * A failed recovery is fatal: opening a partially restored database could destroy user data.
	 */
	fun onAppStart(currentVersionCode: Long, now: Long = System.currentTimeMillis()): Boolean {
		try {
			root.mkdirs()
			if (recoverInterruptedRestore()) {
				pendingFile.delete()
			} else {
				applyPendingRestore()
			}
			val previous = stateFile.takeIf { it.isFile }?.readText()?.trim()?.toLongOrNull()
			var snapshotTaken = false
			if (previous != null && previous != currentVersionCode && hasData()) {
				snapshotTaken = snapshot(previous, now) != null
			}
			if (previous != currentVersionCode) stateFile.writeText(currentVersionCode.toString())
			return snapshotTaken
		} catch (e: Exception) {
			if (rollbackReady.isFile) {
				throw IllegalStateException("A library restore is incomplete. Original data is retained for recovery.", e)
			}
			return false
		}
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
		if (filesDir.usableSpace in 0 until total * 2) return null
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
		val saved = File(root, name).takeIf { SNAPSHOT_NAME.matches(name) && it.isDirectory }
		if (saved == null) {
			pendingFile.delete()
			return
		}
		val incomingFiles = saved.walkTopDown().filter { it.isFile && isRestorable(it.relativeTo(saved).path) }.toList()
		val sourceDatabase = File(saved, "$DB_DIR/$DB_NAME")
		if (!incomingFiles.contains(sourceDatabase) || !validDatabase(sourceDatabase)) {
			pendingFile.delete() // A corrupt or incomplete snapshot must never touch the current library.
			return
		}
		val originals = dataFiles()
		val needed = incomingFiles.sumOf { it.length() } + originals.sumOf { it.length() }
		if (needed > MAX_RESTORE_BYTES || filesDir.usableSpace < needed + FREE_SPACE_RESERVE) {
			return // Keep the request for a later launch; current files remain unchanged.
		}
		if (transaction.exists() && !transaction.deleteRecursively()) throw IOException("Cannot clear old restore staging")
		val staged = File(transaction, "incoming")
		val backup = File(transaction, "original")
		try {
			// Prepare a complete replacement before making any changes to the live database.
			incomingFiles.forEach { file -> file.copyTo(File(staged, file.relativeTo(saved).path), overwrite = true) }
			if (!validDatabase(File(staged, "$DB_DIR/$DB_NAME"))) throw IOException("Invalid staged database")
			if (incomingFiles.any { file ->
				val copy = File(staged, file.relativeTo(saved).path)
				!copy.isFile || copy.length() != file.length()
			}) throw IOException("Incomplete snapshot staging")
			// This is a recoverable journal, not a snapshot to prune. Complete it before touching live data.
			originals.forEach { file -> file.copyTo(File(backup, file.relativeTo(dataDir).path), overwrite = true) }
			if (originals.any { file ->
				val copy = File(backup, file.relativeTo(dataDir).path)
				!copy.isFile || copy.length() != file.length()
			}) throw IOException("Cannot preserve the current library")
			rollbackReady.writeText("ready")
			replaceDatabaseFiles(incomingFiles.filter { it.relativeTo(saved).path.startsWith("$DB_DIR${File.separator}") }
				.map { File(staged, it.relativeTo(saved).path) }, staged)
			incomingFiles.filter { it.relativeTo(saved).path.startsWith("$PREFS_DIR${File.separator}") }
				.forEach { file -> File(staged, file.relativeTo(saved).path).copyTo(File(dataDir, file.relativeTo(saved).path), overwrite = true) }
			pendingFile.delete()
			if (!rollbackReady.delete()) throw IOException("Could not complete restore transaction")
		} catch (e: Exception) {
			if (rollbackReady.isFile) {
				// If rollback itself fails, retain the journal for recovery at the next process start.
				restoreOriginalFiles()
				rollbackReady.delete()
				pendingFile.delete()
			}
			throw e
		} finally {
			if (!rollbackReady.isFile) transaction.deleteRecursively()
		}
	}

	private fun recoverInterruptedRestore(): Boolean {
		if (!rollbackReady.isFile) return false
		restoreOriginalFiles()
		if (!rollbackReady.delete()) throw IOException("Could not close interrupted restore")
		transaction.deleteRecursively()
		return true
	}

	/** Never removes a database component unless a complete rollback copy exists. */
	private fun replaceDatabaseFiles(newFiles: List<File>, sourceRoot: File) {
		val dbDir = File(dataDir, DB_DIR)
		dbDir.listFiles { file -> file.isFile && file.name.startsWith(DB_NAME) }?.forEach { file ->
			if (!file.delete()) throw IOException("Could not replace database: ${file.name}")
		}
		newFiles.forEach { file -> file.copyTo(File(dataDir, file.relativeTo(sourceRoot).path), overwrite = true) }
	}

	private fun restoreOriginalFiles() {
		val originals = File(transaction, "original")
		if (!originals.isDirectory) throw IOException("Restore recovery data is missing")
		val db = File(dataDir, DB_DIR)
		db.listFiles { file -> file.isFile && file.name.startsWith(DB_NAME) }?.forEach { file ->
			if (!file.delete()) throw IOException("Cannot recover database ${file.name}")
		}
		val prefs = File(dataDir, PREFS_DIR)
		prefs.listFiles { file -> file.isFile && file.extension == "xml" }?.forEach { file ->
			if (!file.delete()) throw IOException("Cannot recover preference ${file.name}")
		}
		originals.walkTopDown().filter { it.isFile }.forEach { file ->
			file.copyTo(File(dataDir, file.relativeTo(originals).path), overwrite = true)
		}
	}

	private fun isRestorable(relative: String): Boolean =
		(relative.startsWith("$DB_DIR${File.separator}") && File(relative).name.startsWith(DB_NAME)) ||
			(relative.startsWith("$PREFS_DIR${File.separator}") && File(relative).extension == "xml")

	private fun validDatabase(file: File): Boolean {
		if (!file.isFile || file.length() < SQLITE_HEADER.size) return false
		return file.inputStream().use { input ->
			val bytes = ByteArray(SQLITE_HEADER.size)
			input.read(bytes) == SQLITE_HEADER.size && bytes.contentEquals(SQLITE_HEADER)
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
		private const val RESTORE_TRANSACTION = "restore_transaction"
		private const val ROLLBACK_READY = "rollback_ready"
		private const val KEEP = 2
		private const val MAX_SNAPSHOT_BYTES = 400L * 1024 * 1024
		private const val MAX_RESTORE_BYTES = 800L * 1024 * 1024
		private const val FREE_SPACE_RESERVE = 64L * 1024 * 1024
		private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
		private val SNAPSHOT_NAME = Regex("""(\d+)_(\d+)""")
	}
}
