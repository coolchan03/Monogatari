package org.koitharu.kotatsu.videodl

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimeCoreTest {

	private var now = 0L
	private val logged = mutableListOf<Int>()
	private val core = PlaybackTimeCore({ now }) { logged += it }

	@Test
	fun pausedTimeIsNotCounted() {
		core.setPlaying(true)
		now += 10_000
		core.setPlaying(false)
		now += 600_000
		core.stop()
		assertEquals(listOf(10), logged)
	}

	@Test
	fun flushesEveryThirtySecondsOfPlayback() {
		core.setPlaying(true)
		now += 29_000
		core.tick()
		assertEquals(emptyList<Int>(), logged)
		now += 2_000
		core.tick()
		assertEquals(listOf(31), logged)
		now += 5_000
		core.stop()
		assertEquals(listOf(31, 5), logged)
	}

	@Test
	fun leftoverMillisecondsCarryOver() {
		core.setPlaying(true)
		now += 1_400
		core.stop()
		assertEquals(listOf(1), logged)
		core.setPlaying(true)
		now += 1_600
		core.stop()
		// 400 ms carried over + 1600 ms = 2 s.
		assertEquals(listOf(1, 2), logged)
	}

	@Test
	fun stopWithoutPlayingLogsNothingAndRepeatedStartIsHarmless() {
		core.stop()
		core.setPlaying(true)
		core.setPlaying(true)
		now += 3_000
		core.stop()
		core.stop()
		assertEquals(listOf(3), logged)
	}
}
