package org.koitharu.kotatsu.backups.domain

import android.content.Context
import org.koitharu.kotatsu.R

/** Wording for the backup status shown in settings and in the backup dialog. */
object BackupReminderText {

	/** "3 days ago" / "Never" / "Today". */
	fun ageText(context: Context, lastBackupAt: Long, now: Long = System.currentTimeMillis()): String {
		val age = BackupReminderRules.ageOf(lastBackupAt, now)
		val res = context.resources
		return when (age.kind) {
			BackupReminderRules.AgeKind.NEVER -> context.getString(R.string.backup_last_never)
			BackupReminderRules.AgeKind.TODAY -> context.getString(R.string.backup_last_today)
			BackupReminderRules.AgeKind.DAYS -> res.getQuantityString(R.plurals.backup_last_days, age.count, age.count)
			BackupReminderRules.AgeKind.WEEKS -> res.getQuantityString(R.plurals.backup_last_weeks, age.count, age.count)
			BackupReminderRules.AgeKind.MONTHS -> res.getQuantityString(R.plurals.backup_last_months, age.count, age.count)
		}
	}

	/** "Last backup: 3 days ago". */
	fun lastBackupLine(context: Context, now: Long = System.currentTimeMillis()): String =
		context.getString(R.string.backup_last_line, ageText(context, BackupStatus.lastBackupAt(context), now))

	fun intervalLabel(context: Context, interval: BackupReminderInterval): String = context.getString(
		when (interval) {
			BackupReminderInterval.OFF -> R.string.backup_reminder_off
			BackupReminderInterval.WEEKLY -> R.string.backup_reminder_weekly
			BackupReminderInterval.MONTHLY -> R.string.backup_reminder_monthly
		},
	)
}
