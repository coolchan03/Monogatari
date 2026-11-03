package org.koitharu.kotatsu.backups.domain

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Time of the last successful backup of any kind, plus the reminder setting, in a small prefs file of its own.
 * Call [markBackedUp] at every point where a backup (manual, periodic, folder/WebDAV sync, Nearby send) succeeded.
 */
object BackupStatus {

	const val PREFS_NAME = "monogatari_backup_status"
	const val KEY_LAST_BACKUP_AT = "last_backup_at"
	private const val KEY_REMINDER_INTERVAL = "reminder_interval"
	private const val KEY_LAST_REMINDER_AT = "last_reminder_at"
	private const val KEY_TRACKING_SINCE = "tracking_since"

	fun prefs(context: Context): SharedPreferences =
		context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	fun markBackedUp(context: Context, at: Long = System.currentTimeMillis()) {
		prefs(context).edit { putLong(KEY_LAST_BACKUP_AT, at) }
	}

	/** 0 if no successful backup was ever recorded. */
	fun lastBackupAt(context: Context): Long = prefs(context).getLong(KEY_LAST_BACKUP_AT, 0L)

	fun reminderInterval(context: Context): BackupReminderInterval =
		BackupReminderInterval.fromKey(prefs(context).getString(KEY_REMINDER_INTERVAL, null))

	fun setReminderInterval(context: Context, value: BackupReminderInterval) {
		prefs(context).edit { putString(KEY_REMINDER_INTERVAL, value.key) }
	}

	fun lastReminderAt(context: Context): Long = prefs(context).getLong(KEY_LAST_REMINDER_AT, 0L)

	fun markReminded(context: Context, at: Long = System.currentTimeMillis()) {
		prefs(context).edit { putLong(KEY_LAST_REMINDER_AT, at) }
	}

	/** The first time this was asked, remembered from then on. */
	fun trackingSince(context: Context, now: Long = System.currentTimeMillis()): Long {
		val prefs = prefs(context)
		val stored = prefs.getLong(KEY_TRACKING_SINCE, 0L)
		if (stored > 0L) return stored
		prefs.edit { putLong(KEY_TRACKING_SINCE, now) }
		return now
	}
}
