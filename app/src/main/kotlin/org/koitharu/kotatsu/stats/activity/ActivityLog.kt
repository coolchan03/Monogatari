package org.koitharu.kotatsu.stats.activity

import android.content.Context
import java.util.TimeZone

/**
 * Per-day activity totals for every kind of media, kept in SharedPreferences "monogatari_activity"
 * (one small string per local day: "BOOK:120;VIDEO:30" seconds) for about two years.
 * Callable from any thread: `ActivityLog.record(context, "VIDEO", 90)`.
 *
 * Manga reading time is NOT logged here; it already lives in the stats database and the activity
 * summary reads it from there (see [ActivityStats.merge]).
 */
object ActivityLog {

	private const val PREFS = "monogatari_activity"
	private const val DAY_PREFIX = "d:"
	private const val MAX_SECONDS_PER_CALL = 12 * 3600

	private val lock = Any()
	private var pruned = false

	/** [medium] is MANGA, BOOK, VIDEO or AUDIO (any case); unknown media and non-positive [seconds] are ignored. */
	@JvmStatic
	fun record(context: Context, medium: String, seconds: Int) {
		val name = ActivityStats.normalizeMedium(medium) ?: return
		if (seconds <= 0) return
		val amount = seconds.coerceAtMost(MAX_SECONDS_PER_CALL)
		val today = ActivityStats.epochDayOf(System.currentTimeMillis(), TimeZone.getDefault())
		val key = DAY_PREFIX + ActivityStats.dayKey(today)
		synchronized(lock) {
			val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
			val day = LinkedHashMap(ActivityStats.decode(prefs.getString(key, null)))
			day[name] = (day[name] ?: 0) + amount
			val editor = prefs.edit().putString(key, ActivityStats.encode(day))
			if (!pruned) {
				pruned = true
				for (stored in prefs.all.keys) {
					val epochDay = stored.removePrefix(DAY_PREFIX).let(ActivityStats::parseDayKey)
					if (stored.startsWith(DAY_PREFIX) && (epochDay == null || epochDay <= today - ActivityStats.KEEP_DAYS)) {
						editor.remove(stored)
					}
				}
			}
			editor.apply()
		}
	}

	/** Every logged day as `epochDay -> (medium -> seconds)`. */
	fun load(context: Context): Map<Int, Map<String, Int>> {
		val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		val result = HashMap<Int, Map<String, Int>>()
		synchronized(lock) {
			for ((key, value) in prefs.all) {
				if (!key.startsWith(DAY_PREFIX)) continue
				val day = ActivityStats.parseDayKey(key.removePrefix(DAY_PREFIX)) ?: continue
				val decoded = ActivityStats.decode(value as? String)
				if (decoded.isNotEmpty()) result[day] = decoded
			}
		}
		return result
	}
}
