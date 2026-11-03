package org.koitharu.kotatsu.backups.domain

import org.koitharu.kotatsu.backups.data.BackupCategoryMergePlan
import org.koitharu.kotatsu.backups.data.BackupMergePolicy

/**
 * What a restore would do to the entries of one section, counted without writing anything.
 * Tombstones (deleted entries in the backup) are not part of the counts except [removed].
 */
data class RestorePreviewCounts(
	/** Not on this device yet. */
	val new: Int = 0,
	/** On this device, but the backup version is newer and would replace it. */
	val newer: Int = 0,
	/** On this device and the same version. */
	val same: Int = 0,
	/** On this device, and the local version wins (older in the backup, or deleted here later). */
	val older: Int = 0,
	/** Deleted in the backup after the local entry was last touched: the local entry would be removed. */
	val removed: Int = 0,
) {

	/** Live entries in the backup that are judged against this device. */
	val total: Int get() = new + newer + same + older

	internal fun plus(outcome: RestorePreviewRules.Outcome): RestorePreviewCounts = when (outcome) {
		RestorePreviewRules.Outcome.NEW -> copy(new = new + 1)
		RestorePreviewRules.Outcome.NEWER -> copy(newer = newer + 1)
		RestorePreviewRules.Outcome.SAME -> copy(same = same + 1)
		RestorePreviewRules.Outcome.OLDER -> copy(older = older + 1)
		RestorePreviewRules.Outcome.REMOVES -> copy(removed = removed + 1)
		RestorePreviewRules.Outcome.IGNORED -> this
	}
}

/** Pure counting rules of the restore preview; they mirror what BackupRepository.restoreBackup does. */
internal object RestorePreviewRules {

	enum class Outcome { NEW, NEWER, SAME, OLDER, REMOVES, IGNORED }

	/**
	 * Same arguments as [BackupMergePolicy.shouldReplace]: [localTime] is null when there is no local row,
	 * a [localDeleted] / [incomingDeleted] of 0 means "not deleted".
	 */
	fun classify(localTime: Long?, localDeleted: Long, incomingTime: Long, incomingDeleted: Long): Outcome {
		val replace = BackupMergePolicy.shouldReplace(localTime, localDeleted, incomingTime, incomingDeleted)
		if (incomingDeleted != 0L) {
			return if (replace && localTime != null && localDeleted == 0L) Outcome.REMOVES else Outcome.IGNORED
		}
		return when {
			localTime == null -> Outcome.NEW
			replace -> Outcome.NEWER
			localDeleted == 0L && incomingTime == localTime -> Outcome.SAME
			else -> Outcome.OLDER
		}
	}

	class IncomingCategory(val id: Long, val title: String, val deletedAt: Long)

	/**
	 * [remap] maps incoming category ids to local ones; categories that would be created get negative ids that
	 * match no local row. [new] / [matched] count live incoming categories.
	 */
	class CategoryPlan(val remap: Map<Long, Long>, val new: Int, val matched: Int)

	fun planCategories(
		incoming: List<IncomingCategory>,
		local: List<BackupCategoryMergePlan.LocalCategory>,
	): CategoryPlan {
		val known = ArrayList(local)
		val remap = HashMap<Long, Long>()
		var nextId = -1L
		var new = 0
		var matched = 0
		for (category in incoming) {
			when (val action = BackupCategoryMergePlan.resolve(category.title, category.deletedAt, known)) {
				is BackupCategoryMergePlan.Action.Reuse -> {
					remap[category.id] = action.localId
					if (category.deletedAt == 0L) matched++
				}

				BackupCategoryMergePlan.Action.Insert -> {
					val id = nextId--
					known.add(BackupCategoryMergePlan.LocalCategory(id, category.title, 0L))
					remap[category.id] = id
					new++
				}

				BackupCategoryMergePlan.Action.Skip -> Unit
			}
		}
		return CategoryPlan(remap, new, matched)
	}

	/** Settings that would be applied: the ones that pass the same key filter the restore uses. */
	fun countSettings(keys: Collection<String>, isSafeKey: (String) -> Boolean): Int = keys.count(isSafeKey)
}
