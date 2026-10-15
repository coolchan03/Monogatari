package org.koitharu.kotatsu.sourcehealth

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceActivityLogTest {

	@Test
	fun keepsNewestFirstAndBounded() {
		val log = SourceActivityLog()
		repeat(SourceActivityLog.MAX_ENTRIES + 20) { log.record("s", true, null, now = it.toLong()) }
		val all = log.snapshot()
		assertEquals(SourceActivityLog.MAX_ENTRIES, all.size)
		assertEquals((SourceActivityLog.MAX_ENTRIES + 19).toLong(), all.first().timeMs)
	}

	@Test
	fun agoUsesSensibleUnits() {
		assertEquals("just now", SourceLogFormat.ago(100_000, 70_000))
		assertEquals("5 min ago", SourceLogFormat.ago(5 * 60_000L, 0))
		assertEquals("3 h ago", SourceLogFormat.ago(3 * 3_600_000L + 1, 0))
		assertEquals("2 d ago", SourceLogFormat.ago(2 * 86_400_000L + 1, 0))
	}

	@Test
	fun lineShowsStateAndReason() {
		val entry = SourceActivityLog.Entry(0, "builtin:odysee", false, "HTTP 503")
		assertEquals("2 min ago · builtin:odysee · FAILED - HTTP 503", SourceLogFormat.line(entry, 120_000))
		assertEquals("just now · x · OK", SourceLogFormat.line(SourceActivityLog.Entry(0, "x", true, null), 0))
	}

	@Test
	fun summaryStatusFollowsTheLatestAttempts() {
		val entries = listOf( // newest first
			SourceActivityLog.Entry(30, "a", true, null),
			SourceActivityLog.Entry(20, "a", false, "boom"),
			SourceActivityLog.Entry(25, "b", false, "down"),
			SourceActivityLog.Entry(10, "b", true, null),
			SourceActivityLog.Entry(15, "c", false, "never"),
		)
		val byName = SourceLogFormat.summarize(entries).associateBy { it.source }
		assertEquals("Working", byName.getValue("a").status)
		assertEquals("Limited", byName.getValue("b").status)
		assertEquals("Unreachable", byName.getValue("c").status)
		assertEquals("down", byName.getValue("b").lastError)
	}
}
