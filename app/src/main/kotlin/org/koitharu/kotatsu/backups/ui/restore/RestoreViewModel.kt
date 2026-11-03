package org.koitharu.kotatsu.backups.ui.restore

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.koitharu.kotatsu.backups.data.model.BackupIndex
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.backups.domain.BackupArchiveInspector
import org.koitharu.kotatsu.backups.domain.RestorePreview
import org.koitharu.kotatsu.backups.domain.RestorePreviewCounts
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.util.ext.toUriOrNull
import java.io.FileNotFoundException
import java.io.File
import java.util.zip.ZipFile
import java.io.InputStream
import java.util.Date
import java.util.EnumMap
import java.util.EnumSet
import javax.inject.Inject

@HiltViewModel
class RestoreViewModel @Inject constructor(
	savedStateHandle: SavedStateHandle,
	@ApplicationContext context: Context,
	private val database: MangaDatabase,
) : BaseViewModel() {

	val uri = savedStateHandle.get<String>(AppRouter.KEY_FILE)?.toUriOrNull()
	private val contentResolver = context.contentResolver
    private val cacheDir = context.cacheDir

	val availableEntries = MutableStateFlow<List<BackupSectionModel>>(emptyList())
	val backupDate = MutableStateFlow<Date?>(null)

	/** The staged copy of the backup, kept until the read-only preview has finished with it. */
	@Volatile
	private var stagedArchive: File? = null

	init {
		launchLoadingJob(Dispatchers.Default) {
			loadBackupInfo()
		}
	}

	private suspend fun loadBackupInfo() {
        val sections = runInterruptible(Dispatchers.IO) {
            val source = uri ?: throw FileNotFoundException()
            val staged = File.createTempFile("backup-review-", ".zip", cacheDir)
            try {
                contentResolver.openInputStream(source)?.use {
                    BackupArchiveInspector.copyBounded(it, staged)
                } ?: throw FileNotFoundException("Selected backup is unreadable")
                stagedArchive = staged
                val report = BackupArchiveInspector.inspect(staged)
                if (BackupSection.INDEX in report.sections) {
                    ZipFile(staged).use { zip ->
                        zip.getEntry(BackupSection.INDEX.entryName)?.let { entry ->
                            zip.getInputStream(entry).use { backupDate.value = it.readDate() }
                        }
                    }
                }
                report.sections
            } catch (e: Throwable) {
                stagedArchive = null
                staged.delete()
                throw e
            }
        }
		availableEntries.value = BackupSection.entries.mapNotNull { entry ->
			if (entry == BackupSection.INDEX || entry !in sections) {
				return@mapNotNull null
			}
			BackupSectionModel(
				section = entry,
				isChecked = true,
				isEnabled = true,
			)
		}
		startPreview()
	}

	/**
	 * Read-only dry run for the sections that can describe what would change. A failure only leaves a section
	 * without its preview line; it never blocks or fails the restore.
	 */
	private fun startPreview() {
		val staged = stagedArchive ?: return
		val sections = availableEntries.value.mapNotNullTo(EnumSet.noneOf(BackupSection::class.java)) {
			if (it.section in PREVIEW_SECTIONS) it.section else null
		}
		if (sections.isEmpty()) {
			stagedArchive = null
			staged.delete()
			return
		}
		availableEntries.value = availableEntries.value.map {
			if (it.section in sections) it.copy(isPreviewLoading = true) else it
		}
		viewModelScope.launch(Dispatchers.IO) {
			try {
				RestorePreview.compute(staged, sections, database) { section, counts ->
					withContext(Dispatchers.Main) { setPreview(section, counts) }
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Throwable) {
				e.printStackTraceDebug()
			} finally {
				stagedArchive = null
				staged.delete()
				withContext(NonCancellable + Dispatchers.Main) { setPreview(null, null) }
			}
		}
	}

	/** [section] null: stop showing "Checking" on every section (leaves those without a result blank). */
	private fun setPreview(section: BackupSection?, counts: RestorePreviewCounts?) {
		availableEntries.value = availableEntries.value.map {
			when {
				section == null -> it.copy(isPreviewLoading = false)
				it.section == section -> it.copy(isPreviewLoading = false, preview = counts)
				else -> it
			}
		}
	}

	override fun onCleared() {
		super.onCleared()
		stagedArchive?.delete()
	}

	fun onItemClick(item: BackupSectionModel) {
		val map = availableEntries.value.associateByTo(EnumMap(BackupSection::class.java)) { it.section }
		map[item.section] = item.copy(isChecked = !item.isChecked)
		map.validate()
		availableEntries.value = map.values.sortedBy { it.section.ordinal }
	}

	fun getCheckedSections(): Set<BackupSection> = availableEntries.value
		.mapNotNullTo(EnumSet.noneOf(BackupSection::class.java)) {
			if (it.isChecked) it.section else null
		}

	/**
	 * Check for inconsistent user selection
	 * Favorites cannot be restored without categories
	 */
	private fun MutableMap<BackupSection, BackupSectionModel>.validate() {
		val favorites = this[BackupSection.FAVOURITES] ?: return
		val categories = this[BackupSection.CATEGORIES]
		if (categories?.isChecked == true) {
			if (!favorites.isEnabled) {
				this[BackupSection.FAVOURITES] = favorites.copy(isEnabled = true)
			}
		} else {
			if (favorites.isEnabled) {
				this[BackupSection.FAVOURITES] = favorites.copy(isEnabled = false, isChecked = false)
			}
		}
	}

	private companion object {

		val PREVIEW_SECTIONS = EnumSet.of(
			BackupSection.CATEGORIES,
			BackupSection.FAVOURITES,
			BackupSection.HISTORY,
			BackupSection.BOOKMARKS,
			BackupSection.SETTINGS,
		)
	}

	private fun InputStream.readDate(): Date? = runCatching {
		val index = Json.decodeFromStream<List<BackupIndex>>(this)
		Date(index.single().createdAt)
	}.onFailure { e ->
		e.printStackTraceDebug()
	}.getOrNull()
}
