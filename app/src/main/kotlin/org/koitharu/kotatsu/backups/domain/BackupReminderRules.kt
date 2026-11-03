package org.koitharu.kotatsu.backups.domain

import java.util.concurrent.TimeUnit

/** How often the user wants to be reminded that their data has no recent backup. */
enum class BackupReminderInterval(val key: String, val days: Int) {

	OFF("off", 0),
	WEEKLY("weekly", 7),
	MONTHLY("monthly", 30),
	;

	companion object {

		val DEFAULT = WEEKLY

		fun fromKey(key: String?): BackupReminderInterval = entries.firstOrNull { it.key == key } ?: DEFAULT
	}
}

/** Pure decisions behind the backup reminder, kept free of Android types so they can be unit tested. */
object BackupReminderRules {

	private val DAY_MS = TimeUnit.DAYS.toMillis(1)

	enum class AgeKind { NEVER, TODAY, DAYS, WEEKS, MONTHS }

	/** [count] is the number to show next to [kind]; it is 0 for [AgeKind.NEVER] and [AgeKind.TODAY]. */
	data class Age(val kind: AgeKind, val count: Int)

	/** Whole days between [lastAt] and [now]; a time in the future (clock changes) counts as 0. */
	fun daysSince(lastAt: Long, now: Long): Int {
		if (now <= lastAt) return 0
		return ((now - lastAt) / DAY_MS).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
	}

	/** Wording bucket for "Last backup: ...". [lastBackupAt] <= 0 means no backup was ever recorded. */
	fun ageOf(lastBackupAt: Long, now: Long): Age {
		if (lastBackupAt <= 0L) return Age(AgeKind.NEVER, 0)
		val days = daysSince(lastBackupAt, now)
		return when {
			days < 1 -> Age(AgeKind.TODAY, 0)
			days < 14 -> Age(AgeKind.DAYS, days)
			days < 60 -> Age(AgeKind.WEEKS, days / 7)
			else -> Age(AgeKind.MONTHS, days / 30)
		}
	}

	/**
	 * @param lastBackupAt time of the last successful backup, or 0 if none was recorded
	 * @param trackingSince when the app first started tracking, used as the reference while there is no backup yet
	 * so a new install is not nagged straight away
	 * @param lastReminderAt time the previous reminder was posted, or 0
	 */
	fun isDue(
		interval: BackupReminderInterval,
		lastBackupAt: Long,
		trackingSince: Long,
		lastReminderAt: Long,
		hasLibrary: Boolean,
		now: Long,
	): Boolean {
		if (interval == BackupReminderInterval.OFF || !hasLibrary) return false
		val intervalMs = interval.days * DAY_MS
		val reference = if (lastBackupAt > 0L) lastBackupAt else trackingSince
		if (reference <= 0L || now - reference < intervalMs) return false
		// At most one reminder per interval.
		return lastReminderAt <= 0L || now - lastReminderAt >= intervalMs
	}
}
