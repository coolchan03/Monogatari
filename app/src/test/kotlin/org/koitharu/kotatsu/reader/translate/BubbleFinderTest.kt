package org.koitharu.kotatsu.reader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.reader.translate.TranslationLayout.Box

class BubbleFinderTest {

	private val white = 0xFFFFFFFF.toInt()
	private val ink = 0xFF202020.toInt()

	/** A 40x40 white window with a closed ink outline from (8,8) to (31,31) and some "text" in the middle. */
	private fun bubbleWindow(): IntArray {
		val p = IntArray(40 * 40) { white }
		for (i in 8..31) { p[8 * 40 + i] = ink; p[31 * 40 + i] = ink; p[i * 40 + 8] = ink; p[i * 40 + 31] = ink }
		for (x in 17..22) for (y in 18..21) p[y * 40 + x] = ink
		return p
	}

	@Test
	fun fillsTheInsideOfAClosedOutline() {
		val box = Box(17, 18, 23, 22)
		val seeds = BubbleFinder.seedsAround(box, margin = 2)
		val bounds = BubbleFinder.interiorBounds(bubbleWindow(), 40, 40, seeds, white)
		assertNotNull(bounds)
		assertEquals(Box(9, 9, 31, 31), bounds)
	}

	@Test
	fun openPaperThatLeaksToTheWindowEdgesGivesNothing() {
		val open = IntArray(40 * 40) { white }
		val seeds = BubbleFinder.seedsAround(Box(17, 18, 23, 22), margin = 2)
		assertNull(BubbleFinder.interiorBounds(open, 40, 40, seeds, white))
	}

	@Test
	fun seedsOnInkOrOutsideTheWindowAreIgnored() {
		val allInk = IntArray(40 * 40) { ink }
		assertNull(BubbleFinder.interiorBounds(allInk, 40, 40, listOf(5 to 5, 99 to 99), white))
		assertNull(BubbleFinder.interiorBounds(IntArray(3), 40, 40, listOf(5 to 5), white))
	}

	@Test
	fun tinyColourDriftIsStillPaper() {
		val p = bubbleWindow()
		for (i in p.indices) if (p[i] == white && i % 7 == 0) p[i] = 0xFFF4F4F0.toInt()
		val bounds = BubbleFinder.interiorBounds(p, 40, 40, BubbleFinder.seedsAround(Box(17, 18, 23, 22), 2), white)
		assertEquals(Box(9, 9, 31, 31), bounds)
	}
}
