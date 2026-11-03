package org.koitharu.kotatsu.stats.activity

/** Everything the Stats screen's activity section shows, computed from merged day totals (seconds). */
data class ActivitySummary(
	val week: Map<String, Int>,
	val month: Map<String, Int>,
	val currentStreak: Int,
	val longestStreak: Int,
	val last30Days: List<Map<String, Int>>,
	val hasData: Boolean,
) {

	companion object {
		fun build(days: Map<Int, Map<String, Int>>, today: Int, weekStartsOn: Int): ActivitySummary =
			ActivitySummary(
				week = ActivityStats.weekTotals(days, today, weekStartsOn),
				month = ActivityStats.monthTotals(days, today),
				currentStreak = ActivityStats.currentStreak(days, today),
				longestStreak = ActivityStats.longestStreak(days),
				last30Days = ActivityStats.dailySeries(days, today, 30),
				hasData = ActivityStats.hasAnyActivity(days),
			)
	}
}
