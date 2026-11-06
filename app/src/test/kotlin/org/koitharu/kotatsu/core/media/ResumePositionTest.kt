package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ResumePositionTest {

	@Test
	fun midEpisodeResumesWhereItStopped() {
		assertEquals(600_000L, resumePositionMs(600_000L, 1_400_000L))
	}

	@Test
	fun lastFiveSecondsRestartFromTheBeginning() {
		assertEquals(0L, resumePositionMs(1_395_000L, 1_400_000L))
		assertEquals(0L, resumePositionMs(1_400_000L, 1_400_000L))
		assertEquals(1_394_999L, resumePositionMs(1_394_999L, 1_400_000L))
	}

	@Test
	fun unknownDurationKeepsThePosition() {
		assertEquals(42_000L, resumePositionMs(42_000L, -9_223_372_036_854_775_807L))
		assertEquals(42_000L, resumePositionMs(42_000L, 0L))
	}

	@Test
	fun negativePositionIsZero() {
		assertEquals(0L, resumePositionMs(-5L, 100_000L))
	}
}
