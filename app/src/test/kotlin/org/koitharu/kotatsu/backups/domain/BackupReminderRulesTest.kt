package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

class BackupReminderRulesTest {

	private val day = TimeUnit.DAYS.toMillis(1)
	private val now = 1_000 * day

	private fun age(days: Long, extraMs: Long = 0L) = BackupReminderRules.ageOf(now - days * day - extraMs, now)

	@Test
	fun neverWhenNothingRecorded() {
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.NEVER, 0), BackupReminderRules.ageOf(0L, now))
	}

	@Test
	fun ageBuckets() {
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.TODAY, 0), age(0, day - 1))
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.DAYS, 1), age(1))
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.DAYS, 3), age(3, 5_000))
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.DAYS, 13), age(13))
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.WEEKS, 2), age(14))
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.WEEKS, 8), age(59))
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.MONTHS, 2), age(60))
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.MONTHS, 12), age(365))
	}

	@Test
	fun futureTimestampCountsAsToday() {
		assertEquals(BackupReminderRules.Age(BackupReminderRules.AgeKind.TODAY, 0), BackupReminderRules.ageOf(now + day, now))
		assertEquals(0, BackupReminderRules.daysSince(now + day, now))
	}

	private fun due(
		interval: BackupReminderInterval = BackupReminderInterval.WEEKLY,
		lastBackupDaysAgo: Long? = 10,
		trackingDaysAgo: Long = 100,
		lastReminderDaysAgo: Long? = null,
		hasLibrary: Boolean = true,
	) = BackupReminderRules.isDue(
		interval = interval,
		lastBackupAt = lastBackupDaysAgo?.let { now - it * day } ?: 0L,
		trackingSince = now - trackingDaysAgo * day,
		lastReminderAt = lastReminderDaysAgo?.let { now - it * day } ?: 0L,
		hasLibrary = hasLibrary,
		now = now,
	)

	@Test
	fun dueOnlyWhenBackupIsOlderThanInterval() {
		assertTrue(due(lastBackupDaysAgo = 7))
		assertFalse(due(lastBackupDaysAgo = 6))
		assertFalse(due(BackupReminderInterval.MONTHLY, lastBackupDaysAgo = 29))
		assertTrue(due(BackupReminderInterval.MONTHLY, lastBackupDaysAgo = 30))
	}

	@Test
	fun neverDueWhenOffOrLibraryEmpty() {
		assertFalse(due(BackupReminderInterval.OFF, lastBackupDaysAgo = 400))
		assertFalse(due(lastBackupDaysAgo = 400, hasLibrary = false))
	}

	@Test
	fun atMostOnceUnderOneInterval() {
		assertFalse(due(lastBackupDaysAgo = 20, lastReminderDaysAgo = 3))
		assertTrue(due(lastBackupDaysAgo = 20, lastReminderDaysAgo = 7))
	}

	@Test
	fun withoutAnyBackupTheTrackingStartIsTheReference() {
		assertFalse(due(lastBackupDaysAgo = null, trackingDaysAgo = 2))
		assertTrue(due(lastBackupDaysAgo = null, trackingDaysAgo = 8))
		assertFalse(due(lastBackupDaysAgo = null, trackingDaysAgo = 8, lastReminderDaysAgo = 1))
	}

	@Test
	fun intervalKeysRoundTripAndFallBackToWeekly() {
		BackupReminderInterval.entries.forEach {
			assertEquals(it, BackupReminderInterval.fromKey(it.key))
		}
		assertEquals(BackupReminderInterval.WEEKLY, BackupReminderInterval.fromKey(null))
		assertEquals(BackupReminderInterval.WEEKLY, BackupReminderInterval.fromKey("bogus"))
	}
}
