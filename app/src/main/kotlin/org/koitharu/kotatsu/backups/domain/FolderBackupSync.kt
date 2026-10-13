package org.koitharu.kotatsu.backups.domain

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.backups.data.BackupRepository
import org.koitharu.kotatsu.backups.data.model.BackupIndex
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.io.FileNotFoundException
import java.io.IOException
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/** Opt-in folder synchronization. Only mergeable reading data crosses devices. */
@Singleton
class FolderBackupSync @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: AppSettings,
    private val externalStorage: ExternalBackupStorage,
    private val repository: BackupRepository,
) {
    private val journal = context.getSharedPreferences("monogatari_folder_sync_journal", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()

    suspend fun synchronize(): Int = mutex.withLock {
        withContext(Dispatchers.IO) {
        if (!settings.isFolderSyncEnabled || !settings.isPeriodicalBackupEnabled ||
            settings.periodicalBackupDirectory == null) return@withContext 0
        val ownId = settings.backupDeviceId
        val candidates = scanSnapshots()
        val cursors = candidates.map { it.deviceId }.distinct().associateWith { id ->
            journal.getLong("merged_$id", 0L)
        }
        val pending = FolderSyncPlanner.pending(candidates.map { it.toSnapshot() },
            ownId, cursors, System.currentTimeMillis())
        var merged = 0
        for (snapshot in pending) {
            val uri = Uri.parse(snapshot.uri)
            val staged = File.createTempFile("folder-sync-validated-", ".zip", context.cacheDir)
            val result = try {
                context.contentResolver.openInputStream(uri)?.use {
                    BackupArchiveInspector.copyBounded(it, staged)
                } ?: throw FileNotFoundException("Shared backup no longer exists")
                val report = BackupArchiveInspector.inspect(staged, requireIndex = true)
                if (report.sections.none { it in MERGE_SECTIONS }) {
                    throw IOException("Backup contains no mergeable reading sections")
                }
                ZipInputStream(staged.inputStream()).use { zip ->
                    val index = readIndex(zip)
                    if (index == null || index.deviceId != snapshot.deviceId ||
                        index.createdAt != snapshot.createdAt) {
                        throw IOException("Backup changed while synchronizing")
                    }
                    repository.restoreBackup(zip, MERGE_SECTIONS, null)
                }
            } finally {
                staged.delete()
            }
            if (!result.isAllSuccess) {
                throw IOException("Backup merge failed for ${snapshot.deviceId}", result.failures.first())
            }
            // Commit only after the selected archive has been fully merged.
            check(journal.edit().putLong("merged_${snapshot.deviceId}", snapshot.createdAt).commit()) {
                "Could not save folder sync cursor"
            }
            merged++
        }
        val now = System.currentTimeMillis()
        if (merged > 0 || now - journal.getLong("published_at", 0L) >= SYNC_INTERVAL_MS) {
            publishOwnBackup()
            journal.edit().putLong("published_at", System.currentTimeMillis()).commit()
            trimOwnBackups(ownId)
        }
        journal.edit().putLong("last_checked_at", System.currentTimeMillis()).commit()
        merged
        }
    }

    private suspend fun publishOwnBackup() {
        val temp = File(BackupUtils.getAppBackupDir(context),
            "sync_${UUID.randomUUID()}_" + BackupUtils.generateFileName(context))
        checkNotNull(temp.parentFile).mkdirs()
        try {
            ZipOutputStream(temp.outputStream()).use {
                repository.createBackup(it, null, MERGE_SECTIONS + BackupSection.INDEX)
            }
            BackupArchiveInspector.inspect(temp, requireIndex = true)
            externalStorage.put(temp)
        } finally {
            temp.delete()
        }
    }

    private suspend fun scanSnapshots(): List<SnapshotFile> =
        externalStorage.list()
            .filter { FolderSyncPlanner.isSnapshotFileName(it.fileName) }
            .take(MAX_SCAN_FILES)
            .mapNotNull { file ->
                val index = runCatching {
                    context.contentResolver.openInputStream(file.uri)?.use { source ->
                        ZipInputStream(source).use(::readIndex)
                    }
                }.getOrNull() ?: return@mapNotNull null
                SnapshotFile(file, index.deviceId, index.createdAt)
            }

    private suspend fun trimOwnBackups(deviceId: String) {
        val ownFiles = scanSnapshots().asSequence()
            .filter { it.deviceId == deviceId }
            .sortedWith(compareByDescending<SnapshotFile> { it.createdAt }
                .thenByDescending { it.file.uri.toString() }).toList()
        ownFiles.drop(MAX_OWN_BACKUPS).forEach { externalStorage.delete(it.file) }
    }

    /** Only inspect a bounded index entry, before any database write. */
    private fun readIndex(zip: ZipInputStream): BackupIndex? {
        val first = zip.nextEntry ?: return null
        if (first.name != BackupSection.INDEX.entryName) return null
        val data = ByteArray(MAX_INDEX_BYTES + 1)
        var size = 0
        while (size < data.size) {
            val count = zip.read(data, size, data.size - size)
            if (count < 0) break
            if (count == 0) continue
            size += count
        }
        zip.closeEntry()
        if (size == data.size) return null
        val index = runCatching {
            json.decodeFromString<List<BackupIndex>>(String(data, 0, size, Charsets.UTF_8))
                .singleOrNull()
        }.getOrNull() ?: return null
        // Never accept legacy/anonymous backup files for automatic sync.
        val uuid = runCatching { UUID.fromString(index.deviceId) }.getOrNull() ?: return null
        if (uuid.toString() != index.deviceId.lowercase()) return null
        if (index.appId !in SUPPORTED_APP_IDS ||
            index.appVersion > BuildConfig.VERSION_CODE) return null
        return index
    }

    private data class SnapshotFile(val file: BackupFile, val deviceId: String, val createdAt: Long) {
        fun toSnapshot() = FolderSyncPlanner.Snapshot(file.uri.toString(), deviceId, createdAt)
    }

    private companion object {
        val SUPPORTED_APP_IDS = setOf("io.github.coolchan03.monogatari",
            "io.github.coolchan03.monogatari.debug", "io.github.coolchan03.monogatari.nightly")
        const val MAX_INDEX_BYTES = 16 * 1024
        const val MAX_SCAN_FILES = 200
        const val MAX_OWN_BACKUPS = 3
        val SYNC_INTERVAL_MS = TimeUnit.HOURS.toMillis(6)
        val MERGE_SECTIONS = setOf(BackupSection.HISTORY, BackupSection.CATEGORIES,
            BackupSection.FAVOURITES, BackupSection.BOOKMARKS,
            BackupSection.STATS, BackupSection.CHAPTER_META)
    }
}