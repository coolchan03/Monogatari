package org.koitharu.kotatsu.stats.activity

import java.util.Calendar
import java.util.TimeZone

/**
 * Pure activity maths (no Android types). A "day" is an epoch-day number of the LOCAL calendar date
 * (days since 1970-01-01 of the date the user saw on the clock), so streaks and totals do not depend on
 * the time zone the code runs in. Day totals are `epochDay -> (medium -> seconds)`.
 */
object ActivityStats {

	const val MANGA = "MANGA"
	const val BOOK = "BOOK"
	const val VIDEO = "VIDEO"
	const val AUDIO = "AUDIO"

	val MEDIA: List<String> = listOf(MANGA, BOOK, VIDEO, AUDIO)

	/** A day counts towards a streak once at least this much activity was recorded. */
	const val MIN_ACTIVE_SECONDS = 60

	/** How many days of history are kept. */
	const val KEEP_DAYS = 730

	/** Upper-case [medium] if it is one of [MEDIA], otherwise null. */
	fun normalizeMedium(medium: String): String? = medium.trim().uppercase().takeIf { it in MEDIA }

	// ---- dates ----------------------------------------------------------------------------------

	/** Days since 1970-01-01 of the civil date [year]-[month]-[day] (month 1..12). */
	fun epochDay(year: Int, month: Int, day: Int): Int {
		val y = if (month <= 2) year - 1 else year
		val era = Math.floorDiv(y, 400)
		val yoe = y - era * 400
		val mp = if (month > 2) month - 3 else month + 9
		val doy = (153 * mp + 2) / 5 + day - 1
		val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
		return era * 146097 + doe - 719468
	}

	/** Civil date of [epochDay] as (year, month 1..12, day 1..31). */
	fun civil(epochDay: Int): Triple<Int, Int, Int> {
		val z = epochDay + 719468
		val era = Math.floorDiv(z, 146097)
		val doe = z - era * 146097
		val yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365
		val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
		val mp = (5 * doy + 2) / 153
		val day = doy - (153 * mp + 2) / 5 + 1
		val month = if (mp < 10) mp + 3 else mp - 9
		val year = yoe + era * 400 + if (month <= 2) 1 else 0
		return Triple(year, month, day)
	}

	/** The local calendar day of [millis] in [timezone]. */
	fun epochDayOf(millis: Long, timezone: TimeZone): Int {
		val calendar = Calendar.getInstance(timezone)
		calendar.timeInMillis = millis
		return epochDay(
			calendar.get(Calendar.YEAR),
			calendar.get(Calendar.MONTH) + 1,
			calendar.get(Calendar.DAY_OF_MONTH),
		)
	}

	/** "2026-11-03"; sorts the same as the dates do. */
	fun dayKey(epochDay: Int): String {
		val (y, m, d) = civil(epochDay)
		return String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", y, m, d)
	}

	fun parseDayKey(key: String): Int? {
		val parts = key.split('-')
		if (parts.size != 3) return null
		val y = parts[0].toIntOrNull() ?: return null
		val m = parts[1].toIntOrNull() ?: return null
		val d = parts[2].toIntOrNull() ?: return null
		if (m !in 1..12 || d !in 1..31) return null
		return epochDay(y, m, d)
	}

	/** Index of the weekday of [epochDay], Monday = 0 .. Sunday = 6. */
	fun weekdayIndex(epochDay: Int): Int = Math.floorMod(epochDay + 3, 7)

	/** First day of the week containing [today]; [weekStartsOn] is Monday = 0 .. Sunday = 6. */
	fun weekStart(today: Int, weekStartsOn: Int): Int =
		today - Math.floorMod(weekdayIndex(today) - weekStartsOn, 7)

	/** First day of the month containing [today]. */
	fun monthStart(today: Int): Int = today - (civil(today).third - 1)

	// ---- per-medium value encoding (one day) ---------------------------------------------------

	/** "BOOK:120;VIDEO:30" */
	fun encode(day: Map<String, Int>): String = MEDIA.filter { (day[it] ?: 0) > 0 }
		.joinToString(";") { "$it:${day[it]}" }

	fun decode(value: String?): Map<String, Int> {
		if (value.isNullOrEmpty()) return emptyMap()
		val result = LinkedHashMap<String, Int>()
		for (part in value.split(';')) {
			val medium = normalizeMedium(part.substringBefore(':')) ?: continue
			val seconds = part.substringAfter(':', "").toIntOrNull() ?: continue
			if (seconds > 0) result[medium] = (result[medium] ?: 0) + seconds
		}
		return result
	}

	// ---- totals and streaks ---------------------------------------------------------------------

	fun totalSeconds(day: Map<String, Int>?): Int = day?.values?.sumOf { it.coerceAtLeast(0) } ?: 0

	private fun isActive(days: Map<Int, Map<String, Int>>, day: Int) =
		totalSeconds(days[day]) >= MIN_ACTIVE_SECONDS

	/**
	 * Consecutive active days ending today. If today has no activity yet the streak is still alive
	 * (counted up to yesterday); if yesterday is inactive too it is 0.
	 */
	fun currentStreak(days: Map<Int, Map<String, Int>>, today: Int): Int {
		var day = if (isActive(days, today)) today else today - 1
		var count = 0
		while (isActive(days, day)) {
			count++
			day--
		}
		return count
	}

	fun longestStreak(days: Map<Int, Map<String, Int>>): Int {
		var best = 0
		var run = 0
		var previous = Int.MIN_VALUE
		for (day in days.keys.filter { isActive(days, it) }.sorted()) {
			run = if (previous != Int.MIN_VALUE && day == previous + 1) run + 1 else 1
			if (run > best) best = run
			previous = day
		}
		return best
	}

	/** Seconds per medium (every medium present, 0 if none) for days [from]..[to] inclusive. */
	fun totalsBetween(days: Map<Int, Map<String, Int>>, from: Int, to: Int): Map<String, Int> {
		val result = LinkedHashMap<String, Int>()
		MEDIA.forEach { result[it] = 0 }
		for ((day, values) in days) {
			if (day < from || day > to) continue
			for (medium in MEDIA) result[medium] = result.getValue(medium) + (values[medium] ?: 0).coerceAtLeast(0)
		}
		return result
	}

	fun weekTotals(days: Map<Int, Map<String, Int>>, today: Int, weekStartsOn: Int): Map<String, Int> =
		totalsBetween(days, weekStart(today, weekStartsOn), today)

	fun monthTotals(days: Map<Int, Map<String, Int>>, today: Int): Map<String, Int> =
		totalsBetween(days, monthStart(today), today)

	/** One entry per day, oldest first, ending today: [count] maps medium -> seconds (empty map for no data). */
	fun dailySeries(days: Map<Int, Map<String, Int>>, today: Int, count: Int = 30): List<Map<String, Int>> =
		(today - count + 1..today).map { days[it] ?: emptyMap() }

	// ---- combining sources ----------------------------------------------------------------------

	/**
	 * Merge the activity log with figures read from the manga statistics database. The database is the
	 * source of truth for MANGA (its value replaces anything logged under MANGA, so nothing is counted
	 * twice); any other medium it reports (e.g. novels read in the online reader) is added to the log.
	 */
	fun merge(log: Map<Int, Map<String, Int>>, database: Map<Int, Map<String, Int>>): Map<Int, Map<String, Int>> {
		val result = HashMap<Int, MutableMap<String, Int>>()
		for ((day, values) in log) result[day] = values.toMutableMap()
		for (day in database.keys) {
			val target = result.getOrPut(day) { mutableMapOf() }
			target.remove(MANGA)
			for ((medium, seconds) in database.getValue(day)) {
				if (seconds <= 0) continue
				target[medium] = (if (medium == MANGA) 0 else target[medium] ?: 0) + seconds
			}
		}
		return result
	}

	/** Drop days older than [keepDays] before [today]. */
	fun prune(days: Map<Int, Map<String, Int>>, today: Int, keepDays: Int = KEEP_DAYS): Map<Int, Map<String, Int>> =
		days.filterKeys { it > today - keepDays }

	fun hasAnyActivity(days: Map<Int, Map<String, Int>>): Boolean = days.values.any { totalSeconds(it) > 0 }
}
