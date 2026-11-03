package org.koitharu.kotatsu.backups.ui.restore

import android.content.Context
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.BackupSection
import org.koitharu.kotatsu.backups.domain.RestorePreviewCounts

/** Turns dry-run counts into the line shown under a section in the restore dialog. */
object RestorePreviewText {

	/** Null when the section has no preview line. */
	fun format(context: Context, section: BackupSection, counts: RestorePreviewCounts): String? {
		if (section == BackupSection.SETTINGS) {
			return context.getString(R.string.restore_preview_settings, counts.new)
		}
		if (counts.total == 0 && counts.removed == 0) {
			return context.getString(R.string.restore_preview_empty)
		}
		val parts = ArrayList<String>(5)
		fun add(resId: Int, value: Int) {
			if (value > 0) parts.add(context.getString(resId, value))
		}
		when (section) {
			BackupSection.HISTORY -> {
				add(R.string.restore_preview_newer, counts.newer)
				add(R.string.restore_preview_older, counts.older)
				add(R.string.restore_preview_new, counts.new)
				add(R.string.restore_preview_same, counts.same)
				add(R.string.restore_preview_removed, counts.removed)
			}

			BackupSection.FAVOURITES, BackupSection.CATEGORIES -> {
				add(R.string.restore_preview_new, counts.new)
				add(R.string.restore_preview_same, counts.same + counts.newer)
				add(R.string.restore_preview_older, counts.older)
				add(R.string.restore_preview_removed, counts.removed)
			}

			BackupSection.BOOKMARKS -> {
				add(R.string.restore_preview_new, counts.new)
				add(R.string.restore_preview_same, counts.same)
			}

			else -> return null
		}
		val details = parts.joinToString(", ")
		return when (section) {
			BackupSection.HISTORY -> context.getString(R.string.restore_preview_history, counts.total, details)
			BackupSection.FAVOURITES -> context.getString(R.string.restore_preview_favourites, counts.total, details)
			BackupSection.CATEGORIES -> context.getString(R.string.restore_preview_categories, counts.total, details)
			else -> context.getString(R.string.restore_preview_bookmarks, details)
		}
	}
}
