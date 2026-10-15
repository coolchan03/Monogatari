package org.koitharu.kotatsu.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.media.MediaType

class TapCounterTest {

	@Test
	fun sevenQuickTapsTrigger() {
		val counter = TapCounter()
		val results = (0 until 7).map { counter.tap(it * 300L) }
		assertEquals(listOf(false, false, false, false, false, false, true), results)
	}

	@Test
	fun aPauseResetsTheCount() {
		val counter = TapCounter()
		repeat(5) { counter.tap(it * 200L) }
		assertFalse(counter.tap(10_000L))
		repeat(5) { assertFalse(counter.tap(10_000L + (it + 1) * 200L)) }
		assertTrue(counter.tap(10_000L + 6 * 200L))
	}

	@Test
	fun triggersAgainAfterTheCountRestarts() {
		val counter = TapCounter(needed = 3)
		assertEquals(listOf(false, false, true, false, false, true), (0 until 6).map { counter.tap(it * 100L) })
	}

	@Test
	fun everyMediaTabHasItsOwnForm() {
		val forms = (listOf<MediaType?>(null) + MediaType.entries).map { Mascot.formFor(it) }
		assertEquals(forms.size, forms.toSet().size)
	}
}
