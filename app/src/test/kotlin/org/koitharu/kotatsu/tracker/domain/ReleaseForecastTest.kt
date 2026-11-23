package org.koitharu.kotatsu.tracker.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReleaseForecastTest {

	private val zone = ZoneOffset.UTC
	private fun at(date: String, hour: Int = 12): Long =
		LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli() + hour * 3_600_000L

	private fun weekly(id: Long, title: String, vararg dates: String) =
		dates.map { ReleaseLog(id, title, at(it)) }

	@Test
	fun weeklyTitleDueTomorrowIsThisWeek() {
		val logs = weekly(1, "Weekly", "2026-11-02", "2026-11-09", "2026-11-16")
		val result = ReleaseForecast.build(logs, at("2026-11-22"), zone)
		assertEquals(1, result.size)
		assertEquals(ReleaseForecast.Bucket.THIS_WEEK, result[0].bucket)
		assertEquals(7, result[0].everyDays)
		assertEquals(at("2026-11-23"), result[0].expectedAt)
	}

	@Test
	fun dueTodayAndOverdueAreSeparated() {
		val today = weekly(1, "Today", "2026-11-01", "2026-11-08", "2026-11-15")
		val late = weekly(2, "Late", "2026-10-30", "2026-11-06", "2026-11-13")
		val result = ReleaseForecast.build(today + late, at("2026-11-22"), zone)
		assertEquals(listOf(2L, 1L), result.map { it.mangaId })
		assertEquals(ReleaseForecast.Bucket.OVERDUE, result[0].bucket)
		assertEquals(ReleaseForecast.Bucket.TODAY, result[1].bucket)
	}

	@Test
	fun fewChecksOnOneDayCountAsOneRelease() {
		// Two detections on 11-09 must not create a 'gap' of hours that skews the rhythm.
		val logs = weekly(1, "Busy", "2026-11-02", "2026-11-09", "2026-11-16") +
			ReleaseLog(1, "Busy", at("2026-11-09", 18))
		val result = ReleaseForecast.build(logs, at("2026-11-22"), zone)
		assertEquals(7, result.single().everyDays)
	}

	@Test
	fun sparseHistoryIsNotForecast() {
		val logs = weekly(1, "New", "2026-11-09", "2026-11-16")
		assertTrue(ReleaseForecast.build(logs, at("2026-11-22"), zone).isEmpty())
	}

	@Test
	fun titleOfNewestLogWins() {
		val logs = weekly(1, "Old name", "2026-11-02", "2026-11-09") + ReleaseLog(1, "New name", at("2026-11-16"))
		assertEquals("New name", ReleaseForecast.build(logs, at("2026-11-22"), zone).single().title)
	}

	@Test
	fun abandonedTitleDropsOut() {
		val logs = weekly(1, "Hiatus", "2026-08-01", "2026-08-08", "2026-08-15")
		assertTrue(ReleaseForecast.build(logs, at("2026-11-22"), zone).isEmpty())
	}
}
