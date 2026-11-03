package org.koitharu.kotatsu.stats.activity

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class ActivityStatsTest {

	private fun day(y: Int, m: Int, d: Int) = ActivityStats.epochDay(y, m, d)

	private fun active(vararg days: Int, medium: String = "BOOK", seconds: Int = 600): Map<Int, Map<String, Int>> =
		days.associateWith { mapOf(medium to seconds) }

	@Test fun epochDayRoundTripsAndMatchesKnownDates() {
		assertEquals(0, day(1970, 1, 1))
		assertEquals(19_723, day(2024, 1, 1))
		assertEquals(-1, day(1969, 12, 31))
		for (d in listOf(0, 59, 60, 365, 19_000, 20_500, -400)) {
			val (y, m, dd) = ActivityStats.civil(d)
			assertEquals(d, day(y, m, dd))
		}
		assertEquals(Triple(2024, 2, 29), ActivityStats.civil(day(2024, 2, 29)))
	}

	@Test fun dayKeysRoundTrip() {
		val d = day(2026, 11, 3)
		assertEquals("2026-11-03", ActivityStats.dayKey(d))
		assertEquals(d, ActivityStats.parseDayKey("2026-11-03"))
		assertNull(ActivityStats.parseDayKey("nonsense"))
		assertNull(ActivityStats.parseDayKey("2026-13-01"))
	}

	@Test fun localDayDependsOnTimezoneOnlyThroughTheClock() {
		// 2024-03-10 23:30 UTC is already the 11th in Tokyo and still the 10th in New York.
		val instant = 1_710_113_400_000L
		assertEquals(day(2024, 3, 10), ActivityStats.epochDayOf(instant, TimeZone.getTimeZone("UTC")))
		assertEquals(day(2024, 3, 11), ActivityStats.epochDayOf(instant, TimeZone.getTimeZone("Asia/Tokyo")))
		assertEquals(day(2024, 3, 10), ActivityStats.epochDayOf(instant, TimeZone.getTimeZone("America/New_York")))
		// Day keys are plain dates: the same stored key means the same day everywhere.
		assertEquals(day(2024, 3, 11), ActivityStats.parseDayKey("2024-03-11"))
	}

	@Test fun encodesAndDecodesDays() {
		val encoded = ActivityStats.encode(mapOf("VIDEO" to 30, "BOOK" to 120, "AUDIO" to 0))
		assertEquals("BOOK:120;VIDEO:30", encoded)
		assertEquals(mapOf("BOOK" to 120, "VIDEO" to 30), ActivityStats.decode(encoded))
		assertEquals(emptyMap<String, Int>(), ActivityStats.decode(null))
		assertEquals(mapOf("BOOK" to 5), ActivityStats.decode("junk;book:5;NEWS:9;VIDEO:x"))
	}

	@Test fun noDataMeansNoStreaks() {
		val empty = emptyMap<Int, Map<String, Int>>()
		assertEquals(0, ActivityStats.currentStreak(empty, day(2026, 11, 3)))
		assertEquals(0, ActivityStats.longestStreak(empty))
		assertFalse(ActivityStats.hasAnyActivity(empty))
		assertEquals(0, ActivityStats.totalsBetween(empty, 0, 10).values.sum())
		assertEquals(30, ActivityStats.dailySeries(empty, day(2026, 11, 3)).size)
	}

	@Test fun currentStreakCountsBackFromToday() {
		val today = day(2026, 11, 3)
		val days = active(today, today - 1, today - 2, today - 3)
		assertEquals(4, ActivityStats.currentStreak(days, today))
	}

	@Test fun currentStreakSurvivesAnInactiveToday() {
		val today = day(2026, 11, 3)
		assertEquals(2, ActivityStats.currentStreak(active(today - 1, today - 2), today))
	}

	@Test fun currentStreakBreaksAfterAMissedDay() {
		val today = day(2026, 11, 3)
		assertEquals(0, ActivityStats.currentStreak(active(today - 2, today - 3), today))
		// A gap inside the run ends it.
		assertEquals(2, ActivityStats.currentStreak(active(today, today - 1, today - 3), today))
	}

	@Test fun tooLittleActivityDoesNotCountAsADay() {
		val today = day(2026, 11, 3)
		val days = active(today, seconds = ActivityStats.MIN_ACTIVE_SECONDS - 1)
		assertEquals(0, ActivityStats.currentStreak(days, today))
		val enough = active(today, seconds = ActivityStats.MIN_ACTIVE_SECONDS)
		assertEquals(1, ActivityStats.currentStreak(enough, today))
	}

	@Test fun streakAcrossMonthAndYearBoundary() {
		val today = day(2027, 1, 2)
		val days = active(day(2026, 12, 30), day(2026, 12, 31), day(2027, 1, 1), today)
		assertEquals(4, ActivityStats.currentStreak(days, today))
		assertEquals(4, ActivityStats.longestStreak(days))
	}

	@Test fun longestStreakFindsTheBestRun() {
		val base = day(2026, 5, 1)
		val days = active(base, base + 1, base + 5, base + 6, base + 7, base + 8, base + 20)
		assertEquals(4, ActivityStats.longestStreak(days))
	}

	@Test fun weekStartHonoursFirstDayOfWeek() {
		val wednesday = day(2026, 11, 4)
		assertEquals(2, ActivityStats.weekdayIndex(wednesday))
		assertEquals(day(2026, 11, 2), ActivityStats.weekStart(wednesday, 0))
		assertEquals(day(2026, 11, 1), ActivityStats.weekStart(wednesday, 6))
		assertEquals(wednesday, ActivityStats.weekStart(wednesday, 2))
		// A Monday that is also the start of the week.
		assertEquals(day(2026, 11, 2), ActivityStats.weekStart(day(2026, 11, 2), 0))
	}

	@Test fun monthStartHandlesMonthEdges() {
		assertEquals(day(2026, 2, 1), ActivityStats.monthStart(day(2026, 2, 28)))
		assertEquals(day(2024, 2, 1), ActivityStats.monthStart(day(2024, 2, 29)))
		assertEquals(day(2026, 11, 1), ActivityStats.monthStart(day(2026, 11, 1)))
	}

	@Test fun weekAndMonthTotalsPerMedium() {
		val today = day(2026, 11, 4) // Wednesday
		val days = mapOf(
			today to mapOf("BOOK" to 600, "VIDEO" to 60),
			today - 2 to mapOf("MANGA" to 300), // Monday: in this week
			today - 3 to mapOf("AUDIO" to 120), // Sunday: previous week, same month
			day(2026, 10, 31) to mapOf("BOOK" to 999), // previous month
		)
		val week = ActivityStats.weekTotals(days, today, 0)
		assertEquals(mapOf("MANGA" to 300, "BOOK" to 600, "VIDEO" to 60, "AUDIO" to 0), week)
		val month = ActivityStats.monthTotals(days, today)
		assertEquals(mapOf("MANGA" to 300, "BOOK" to 600, "VIDEO" to 60, "AUDIO" to 120), month)
	}

	@Test fun monthTotalsAtTheFirstOfTheMonthOnlyCountToday() {
		val today = day(2026, 12, 1)
		val days = mapOf(today to mapOf("BOOK" to 60), today - 1 to mapOf("BOOK" to 500))
		assertEquals(60, ActivityStats.monthTotals(days, today).getValue("BOOK"))
	}

	@Test fun dailySeriesIsOldestFirstAndFillsGaps() {
		val today = day(2026, 11, 3)
		val days = mapOf(today to mapOf("BOOK" to 10), today - 29 to mapOf("VIDEO" to 20), today - 30 to mapOf("BOOK" to 99))
		val series = ActivityStats.dailySeries(days, today, 30)
		assertEquals(30, series.size)
		assertEquals(mapOf("VIDEO" to 20), series.first())
		assertEquals(mapOf("BOOK" to 10), series.last())
		assertTrue(series[10].isEmpty())
	}

	@Test fun mergeLetsTheDatabaseOwnMangaAndAddsOtherMedia() {
		val d = day(2026, 11, 3)
		val log = mapOf(d to mapOf("MANGA" to 9999, "BOOK" to 100, "VIDEO" to 50))
		val database = mapOf(d to mapOf("MANGA" to 300, "BOOK" to 40), d - 1 to mapOf("MANGA" to 60))
		val merged = ActivityStats.merge(log, database)
		assertEquals(mapOf("MANGA" to 300, "BOOK" to 140, "VIDEO" to 50), merged[d])
		assertEquals(mapOf("MANGA" to 60), merged[d - 1])
		// inputs untouched
		assertEquals(9999, log.getValue(d).getValue("MANGA"))
	}

	@Test fun pruneDropsOldDays() {
		val today = day(2026, 11, 3)
		val days = active(today, today - 729, today - 730, today - 900)
		assertEquals(setOf(today, today - 729), ActivityStats.prune(days, today).keys)
	}

	@Test fun summaryCombinesEverything() {
		val today = day(2026, 11, 4)
		val summary = ActivitySummary.build(active(today, today - 1, today - 5), today, 0)
		assertTrue(summary.hasData)
		assertEquals(2, summary.currentStreak)
		assertEquals(2, summary.longestStreak)
		assertEquals(30, summary.last30Days.size)
		assertEquals(1200, summary.week.getValue("BOOK"))
		// today - 5 is 30 October: last month, so it counts for neither the week nor the month.
		assertEquals(1200, summary.month.getValue("BOOK"))
	}
}
