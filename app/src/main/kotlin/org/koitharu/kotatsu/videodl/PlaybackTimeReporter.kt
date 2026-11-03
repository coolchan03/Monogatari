package org.koitharu.kotatsu.videodl

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.koitharu.kotatsu.stats.activity.ActivityLog

/**
 * Counts only the time a player is actually playing (not paused or buffering) and hands whole
 * seconds to [sink] every [FLUSH_MS] and when [stop] is called. Pure: the clock is injected.
 */
class PlaybackTimeCore(
	private val clock: () -> Long,
	private val sink: (seconds: Int) -> Unit,
) {

	private var playingSince = -1L
	private var pendingMs = 0L

	fun setPlaying(playing: Boolean) {
		val now = clock()
		if (playing) {
			if (playingSince < 0L) playingSince = now
		} else if (playingSince >= 0L) {
			pendingMs += (now - playingSince).coerceAtLeast(0L)
			playingSince = -1L
		}
	}

	/** Periodic call; flushes once [FLUSH_MS] of playback has piled up. */
	fun tick() {
		if (playingSince >= 0L) {
			val now = clock()
			pendingMs += (now - playingSince).coerceAtLeast(0L)
			playingSince = now
		}
		flush(force = false)
	}

	fun stop() {
		setPlaying(false)
		flush(force = true)
	}

	private fun flush(force: Boolean) {
		if (!force && pendingMs < FLUSH_MS) return
		val seconds = (pendingMs / 1000L).toInt()
		if (seconds <= 0) return
		pendingMs -= seconds * 1000L
		sink(seconds)
	}

	companion object {
		const val FLUSH_MS = 30_000L
	}
}

/**
 * Android wrapper that feeds watch/listen time into the activity log. Call [setPlaying] from the
 * player's onIsPlayingChanged and [stop] when playback ends or the screen goes away.
 */
class PlaybackTimeReporter(context: Context, private val medium: String) {

	private val appContext = context.applicationContext
	private val core = PlaybackTimeCore({ SystemClock.elapsedRealtime() }) { seconds -> record(appContext, medium, seconds) }
	private val handler = Handler(Looper.getMainLooper())
	private val ticker = object : Runnable {
		override fun run() {
			core.tick()
			handler.postDelayed(this, PlaybackTimeCore.FLUSH_MS)
		}
	}

	fun setPlaying(playing: Boolean) {
		core.setPlaying(playing)
		handler.removeCallbacks(ticker)
		if (playing) handler.postDelayed(ticker, PlaybackTimeCore.FLUSH_MS)
	}

	fun stop() {
		handler.removeCallbacks(ticker)
		core.stop()
	}

	companion object {
		const val VIDEO = "VIDEO"
		const val AUDIO = "AUDIO"

		/** The single place that talks to the stats module; adjust here if its API changes. */
		fun record(context: Context, medium: String, seconds: Int) {
			runCatching { ActivityLog.record(context, medium, seconds) }
		}
	}
}
