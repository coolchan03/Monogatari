package org.koitharu.kotatsu.stats.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityTimerCoreTest {

	private var now = 1_000L
	private val recorded = mutableListOf<Pair<String, Int>>()
	private val timer = ActivityTimerCore("BOOK", { now }) { medium, seconds -> recorded += medium to seconds }

	@Test fun pauseFlushesTheElapsedSeconds() {
		timer.resume()
		now += 42_500L
		timer.pause()
		assertEquals(listOf("BOOK" to 42), recorded)
	}

	@Test fun tickFlushesOnlyOncePerMinute() {
		timer.resume()
		now += 30_000L
		timer.tick()
		assertEquals(emptyList<Pair<String, Int>>(), recorded)
		now += 30_000L
		timer.tick()
		assertEquals(listOf("BOOK" to 60), recorded)
	}

	@Test fun timeAfterFiveIdleMinutesIsNotCounted() {
		timer.resume()
		now += 20 * 60_000L // user walked away
		timer.pause()
		assertEquals(listOf("BOOK" to 300), recorded)
	}

	@Test fun interactionKeepsTheClockRunning() {
		timer.resume()
		repeat(4) {
			now += 4 * 60_000L
			timer.interaction()
		}
		now += 60_000L
		timer.pause()
		assertEquals(16 * 60 + 60, recorded.sumOf { it.second })
	}

	@Test fun interactionAfterALongGapOnlyCreditsTheIdleWindow() {
		timer.resume()
		now += 60 * 60_000L
		timer.interaction()
		now += 10_000L
		timer.pause()
		assertEquals(300 + 10, recorded.sumOf { it.second })
	}

	@Test fun ignoresEventsWhileNotRunningAndDoubleResume() {
		timer.interaction()
		timer.tick()
		timer.pause()
		timer.resume()
		now += 10_000L
		timer.resume()
		now += 10_000L
		timer.pause()
		timer.pause()
		assertEquals(listOf("BOOK" to 20), recorded)
	}

	@Test fun leftoverMillisecondsCarryOver() {
		timer.resume()
		now += 1_600L
		timer.pause()
		now += 5_000L
		timer.resume()
		now += 1_600L
		timer.pause()
		assertEquals(listOf("BOOK" to 1, "BOOK" to 2), recorded)
	}
}
