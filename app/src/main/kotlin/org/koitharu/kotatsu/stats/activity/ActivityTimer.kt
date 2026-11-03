package org.koitharu.kotatsu.stats.activity

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Activity-side wrapper: call [resume] from onResume, [pause] from onPause and [interaction] from
 * onUserInteraction. Writes to [ActivityLog] as [medium] (BOOK by default).
 */
class ReadingTimer(context: Context, medium: String = ActivityStats.BOOK) {

	private val appContext = context.applicationContext
	private val core = ActivityTimerCore(medium, { SystemClock.elapsedRealtime() }) { m, seconds ->
		ActivityLog.record(appContext, m, seconds)
	}
	private val handler = Handler(Looper.getMainLooper())
	private val ticker = object : Runnable {
		override fun run() {
			core.tick()
			handler.postDelayed(this, ActivityTimerCore.FLUSH_MS)
		}
	}

	fun resume() {
		core.resume()
		handler.removeCallbacks(ticker)
		handler.postDelayed(ticker, ActivityTimerCore.FLUSH_MS)
	}

	fun interaction() = core.interaction()

	fun pause() {
		handler.removeCallbacks(ticker)
		core.pause()
	}
}
