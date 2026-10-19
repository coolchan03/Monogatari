package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ReadingScrollPositionTest {

	@Test
	fun sameRelativePlaceAfterTheTextReflows() {
		// Halfway through a portrait chapter (content 10,000px, screen 2,000px)...
		val fraction = ReadingScrollPosition.fraction(4_000, 10_000, 2_000)
		assertEquals(0.5f, fraction, 0.0001f)
		// ...is halfway through the same chapter re-wrapped in landscape (content 6,000px, screen 1,000px).
		assertEquals(2_500, ReadingScrollPosition.offset(fraction, 6_000, 1_000))
	}

	@Test
	fun shortOrUnlaidContentStaysAtTheTop() {
		assertEquals(0f, ReadingScrollPosition.fraction(50, 800, 1_000), 0f)
		assertEquals(0, ReadingScrollPosition.offset(0.7f, 0, 0))
		assertEquals(1f, ReadingScrollPosition.fraction(99_999, 10_000, 2_000), 0f)
		assertEquals(0, ReadingScrollPosition.offset(Float.NaN, 10_000, 2_000))
	}
}
