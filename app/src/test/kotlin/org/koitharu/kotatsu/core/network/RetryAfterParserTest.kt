package org.koitharu.kotatsu.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class RetryAfterParserTest {

	private val now = 1_800_000_000_000L // fixed clock

	@Test
	fun deltaSeconds() {
		assertEquals(120_000L, RetryAfterParser.parseDelayMillis("120", now))
		assertEquals(5_000L, RetryAfterParser.parseDelayMillis(" 5 ", now))
	}

	@Test
	fun httpDateIsConvertedToADelayNotAnEpoch() {
		val target = java.time.Instant.ofEpochMilli(now + 30_000)
			.atZone(java.time.ZoneOffset.UTC)
			.format(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
		val delay = RetryAfterParser.parseDelayMillis(target, now)
		// RFC 1123 has second resolution
		assertEquals(30_000L, delay)
	}

	@Test
	fun pastDateAndNegativeSecondsMeanNoDelay() {
		assertEquals(0L, RetryAfterParser.parseDelayMillis("Wed, 21 Oct 2015 07:28:00 GMT", now))
		assertEquals(0L, RetryAfterParser.parseDelayMillis("-30", now))
		assertEquals(0L, RetryAfterParser.parseDelayMillis("0", now))
	}

	@Test
	fun hugeValuesAreClamped() {
		assertEquals(RetryAfterParser.MAX_DELAY_MS, RetryAfterParser.parseDelayMillis("999999999999", now))
		assertEquals(RetryAfterParser.MAX_DELAY_MS, RetryAfterParser.parseDelayMillis(Long.MAX_VALUE.toString(), now))
		assertEquals(RetryAfterParser.MAX_DELAY_MS, RetryAfterParser.parseDelayMillis("Fri, 31 Dec 9999 23:59:59 GMT", now))
	}

	@Test
	fun malformedAndMissingNeverThrow() {
		listOf(null, "", "  ", "soon", "12abc", "Tue, 99 Foo 2026 25:61:61 GMT", "1e3").forEach {
			assertEquals(it, 0L, RetryAfterParser.parseDelayMillis(it, now))
		}
	}
}
