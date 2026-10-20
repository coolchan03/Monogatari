package org.koitharu.kotatsu.sync.conflict

import android.content.Context
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.DecodeSequenceMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeToSequence
import kotlinx.serialization.serializer
import org.json.JSONArray
import org.koitharu.kotatsu.backups.data.model.HistoryBackup
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.core.db.MangaDatabase
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject

/**
 * Read-only lookups for the Nearby conflict review: the received archive's history and the matching local rows.
 * The only thing it writes is its own "last synced with device X" record, kept in a separate preferences file.
 */
@Reusable
class HistoryConflictRepository @Inject constructor(
	private val database: MangaDatabase,
	@ApplicationContext private val context: Context,
) {

	/** What the review sheet shows; [chapterNumbers] maps chapter ids to their numbers where this device knows them. */
	class Review(
		val deviceId: String,
		val conflicts: List<HistoryConflicts.Conflict>,
		val chapterNumbers: Map<Long, Float>,
	)

	private val json = Json {
		allowSpecialFloatingPointValues = true
		coerceInputValues = true
		ignoreUnknownKeys = true
	}

	private val ledger by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

	/** Blocking; call off the main thread. */
	suspend fun review(archive: File): Review {
		var deviceId = ""
		val incoming = ArrayList<HistoryConflicts.Snapshot>()
		ZipInputStream(archive.inputStream()).use { input ->
			var entry = input.nextEntry
			while (entry != null) {
				when (BackupSection.of(entry)) {
					BackupSection.INDEX -> deviceId = runCatching {
						JSONArray(input.readBytes().decodeToString()).optJSONObject(0)?.optString("device_id").orEmpty()
					}.getOrDefault("")

					BackupSection.HISTORY -> json.decodeToSequence(
						input,
						serializer<HistoryBackup>(),
						DecodeSequenceMode.ARRAY_WRAPPED,
					).forEach { incoming += it.toSnapshot() }

					else -> Unit
				}
				input.closeEntry()
				entry = input.nextEntry
			}
		}
		val dao = database.getHistoryDao()
		val local = HashMap<Long, HistoryConflicts.Snapshot>()
		for (theirs in incoming) {
			val row = dao.findIncludingDeleted(theirs.mangaId) ?: continue
			local[theirs.mangaId] = HistoryConflicts.Snapshot(
				mangaId = row.mangaId,
				title = theirs.title,
				chapterId = row.chapterId,
				page = row.page,
				percent = row.percent,
				updatedAt = row.updatedAt,
				deletedAt = row.deletedAt,
			)
		}
		val conflicts = HistoryConflicts.find(local, incoming, lastSyncAt(deviceId))
		val numbers = HashMap<Long, Float>()
		for (conflict in conflicts) {
			database.getChaptersDao().findAll(conflict.mangaId).forEach { numbers[it.chapterId] = it.number }
		}
		return Review(deviceId, conflicts, numbers)
	}

	/** 0 (first sync: every difference is asked about) when the other device is unknown. */
	fun lastSyncAt(deviceId: String): Long =
		if (deviceId.isBlank()) 0L else ledger.getLong(KEY_PREFIX + deviceId, 0L)

	fun markSynced(deviceId: String, time: Long = System.currentTimeMillis()) {
		if (deviceId.isNotBlank()) ledger.edit().putLong(KEY_PREFIX + deviceId, time).apply()
	}

	private fun HistoryBackup.toSnapshot() = HistoryConflicts.Snapshot(
		mangaId = mangaId,
		title = manga.title,
		chapterId = chapterId,
		page = page,
		percent = percent,
		updatedAt = updatedAt,
		deletedAt = deletedAt,
	)

	private companion object {
		const val PREFS_NAME = "monogatari_nearby_sync_ledger"
		const val KEY_PREFIX = "last_sync_"
	}
}
