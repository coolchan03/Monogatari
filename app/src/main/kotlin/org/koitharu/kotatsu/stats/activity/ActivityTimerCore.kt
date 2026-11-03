package org.koitharu.kotatsu.stats.activity

/**
 * Accumulates foreground time between [resume] and [pause] and hands whole seconds to [sink] (at least every
 * [FLUSH_MS] while running and on [pause]). Time stops counting [IDLE_MS] after the last [interaction], so a
 * book left open on the table does not run up the clock. Pure: the clock is injected, so it is unit-tested.
 */
class ActivityTimerCore(
	private val medium: String,
	private val clock: () -> Long,
	private val sink: (medium: String, seconds: Int) -> Unit,
) {

	private var running = false
	private var lastTick = 0L
	private var lastInteraction = 0L
	private var pendingMs = 0L

	fun resume() {
		if (running) return
		val now = clock()
		running = true
		lastTick = now
		lastInteraction = now
	}

	fun interaction() {
		if (!running) return
		val now = clock()
		accumulate(now)
		lastInteraction = now
	}

	/** Periodic call while running; flushes when a minute has piled up. */
	fun tick() {
		if (!running) return
		accumulate(clock())
		flush(force = false)
	}

	fun pause() {
		if (!running) return
		accumulate(clock())
		running = false
		flush(force = true)
	}

	private fun accumulate(now: Long) {
		val countUntil = minOf(now, lastInteraction + IDLE_MS)
		if (countUntil > lastTick) pendingMs += countUntil - lastTick
		lastTick = now
	}

	private fun flush(force: Boolean) {
		if (!force && pendingMs < FLUSH_MS) return
		val seconds = (pendingMs / 1000L).toInt()
		if (seconds <= 0) return
		pendingMs -= seconds * 1000L
		sink(medium, seconds)
	}

	companion object {
		const val IDLE_MS = 5 * 60_000L
		const val FLUSH_MS = 60_000L
	}
}
