package org.koitharu.kotatsu.download.ui.list

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadsSummaryTest {

	private val now = 1_000_000L

	@Test
	fun emptyQueueIsNotVisible() {
		val summary = DownloadsSummary.of(emptyList(), now)
		assertFalse(summary.isVisible)
		assertNull(summary.eta)
	}

	@Test
	fun finishedRowsAreNotCounted() {
		val summary = DownloadsSummary.of(listOf(DownloadBucket.FINISHED to 0L, DownloadBucket.FINISHED to 0L), now)
		assertFalse(summary.isVisible)
	}

	@Test
	fun countsEachBucket() {
		val summary = DownloadsSummary.of(
			listOf(
				DownloadBucket.DOWNLOADING to 0L,
				DownloadBucket.QUEUED to 0L,
				DownloadBucket.QUEUED to 0L,
				DownloadBucket.PAUSED to 0L,
				DownloadBucket.FAILED to 0L,
				DownloadBucket.FAILED to 0L,
				DownloadBucket.FAILED to 0L,
				DownloadBucket.FINISHED to 0L,
			),
			now,
		)
		assertEquals(1, summary.downloading)
		assertEquals(2, summary.queued)
		assertEquals(1, summary.paused)
		assertEquals(3, summary.failed)
		assertTrue(summary.isVisible)
	}

	@Test
	fun remainingTimeUsesLatestEtaOfRunningDownloadsOnly() {
		val summary = DownloadsSummary.of(
			listOf(
				DownloadBucket.DOWNLOADING to now + 30_000L,
				DownloadBucket.DOWNLOADING to now + 300_000L,
				DownloadBucket.PAUSED to now + 9_000_000L,
				DownloadBucket.QUEUED to now + 9_000_000L,
			),
			now,
		)
		assertEquals(300_000L, summary.remainingMs)
	}

	@Test
	fun elapsedOrUnknownEtaIsZero() {
		assertEquals(0L, DownloadsSummary.of(listOf(DownloadBucket.DOWNLOADING to now - 5L), now).remainingMs)
		assertEquals(0L, DownloadsSummary.of(listOf(DownloadBucket.DOWNLOADING to -1L), now).remainingMs)
	}

	@Test
	fun etaFormatting() {
		assertNull(DownloadsSummary.formatEta(0L))
		assertNull(DownloadsSummary.formatEta(-10L))
		assertEquals(DownloadEta(DownloadEta.Scale.LESS_THAN_MINUTE, 0), DownloadsSummary.formatEta(59_999L))
		assertEquals(DownloadEta(DownloadEta.Scale.MINUTES, 1), DownloadsSummary.formatEta(60_000L))
		assertEquals(DownloadEta(DownloadEta.Scale.MINUTES, 2), DownloadsSummary.formatEta(60_001L))
		assertEquals(DownloadEta(DownloadEta.Scale.MINUTES, 59), DownloadsSummary.formatEta(59 * 60_000L))
		assertEquals(DownloadEta(DownloadEta.Scale.HOURS, 1), DownloadsSummary.formatEta(60 * 60_000L))
		assertEquals(DownloadEta(DownloadEta.Scale.HOURS, 2), DownloadsSummary.formatEta(90 * 60_000L))
		assertEquals(DownloadEta(DownloadEta.Scale.HOURS, 5), DownloadsSummary.formatEta(5 * 3_600_000L))
	}
}
