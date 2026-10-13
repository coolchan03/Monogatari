package org.koitharu.kotatsu.jsext

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.TimeZone

class JsDateParserTest {

	private val utc = TimeZone.getTimeZone("UTC")
	private val now = 1_760_000_000_000L // 2025-10-09T08:53:20Z
	private val parser = JsDateParser(now = { now }, zone = utc)

	@Test
	fun epochNumbersAndIso() {
		assertEquals(1_700_000_000_000L, parser.parse("1700000000", "", ""))
		assertEquals(1_700_000_000_123L, parser.parse("1700000000123", "", ""))
		assertEquals(1_704_067_200_000L, parser.parse("2024-01-01T00:00:00Z", "", ""))
	}

	@Test
	fun explicitAndCommonFormats() {
		assertEquals(1_704_412_800_000L, parser.parse("05/01/2024", "dd/MM/yyyy", "en"))
		assertEquals(1_704_412_800_000L, parser.parse("January 5th, 2024", "", ""))
		assertEquals(1_704_412_800_000L, parser.parse("2024-01-05", "", ""))
		assertEquals(1_704_412_800_000L, parser.parse("5 janvier 2024", "", "fr"))
	}

	@Test
	fun relativeDates() {
		val hour = 3_600_000L
		assertEquals(now - 3 * hour, parser.parse("3 hours ago", "", ""))
		assertEquals(now - 5 * 60_000L, parser.parse("5 minutes ago", "", ""))
		assertEquals(now - 2 * 24 * hour, parser.parse("2 days ago", "", ""))
		assertEquals(now - 2 * 24 * hour, parser.parse("hace 2 días", "", ""))
		assertEquals(now - 7 * 24 * hour, parser.parse("1 week ago", "", ""))
		assertEquals(now - 30 * 24 * hour, parser.parse("1 month ago", "", ""))
		assertEquals(1_759_968_000_000L, parser.parse("Today", "", "")) // midnight of 2025-10-09
		assertEquals(1_759_881_600_000L, parser.parse("Yesterday", "", ""))
	}

	@Test
	fun parseAllFallsBackToCallTimeAndSkipsBlanks() {
		assertEquals(
			listOf("1700000000000", now.toString()),
			parser.parseAll(listOf("1700000000", "", "complete nonsense"), "", ""),
		)
	}
}
