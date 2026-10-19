package org.koitharu.kotatsu.reader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
	fun irregularBubbleMaskLeavesCornersAndArtworkOutside() {
		val pixels = IntArray(40 * 40) { ink }
		// Diamond-shaped paper area. Its bounding box includes plenty of ink outside the bubble.
		for (y in 8..32) for (x in 8..32) {
			if (kotlin.math.abs(x - 20) + kotlin.math.abs(y - 20) <= 12) pixels[y * 40 + x] = white
		}
		val region = BubbleFinder.interior(pixels, 40, 40, listOf(20 to 20), white)
		assertNotNull(region)
		assertEquals(Box(8, 8, 33, 33), region!!.bounds)
		assertEquals(true, region.contains(20, 20))
		assertEquals(false, region.contains(8, 8))
		assertEquals(false, region.contains(30, 30))
	}

	@Test
	fun tinyColourDriftIsStillPaper() {
		val p = bubbleWindow()
		for (i in p.indices) if (p[i] == white && i % 7 == 0) p[i] = 0xFFF4F4F0.toInt()
		val bounds = BubbleFinder.interiorBounds(p, 40, 40, BubbleFinder.seedsAround(Box(17, 18, 23, 22), 2), white)
		assertEquals(Box(9, 9, 31, 31), bounds)
	}

	@Test
	fun inscribedRectangleOfAnOvalIsNarrowerThanItsBounds() {
		// An ellipse 60 wide and 30 tall: rows are short near the top and bottom.
		val rows = 30
		val firsts = IntArray(rows)
		val lasts = IntArray(rows)
		for (y in 0 until rows) {
			val dy = (y + 0.5) / rows * 2 - 1
			val half = (30 * kotlin.math.sqrt(1 - dy * dy)).toInt()
			firsts[y] = 30 - half
			lasts[y] = 29 + half
		}
		val box = BubbleFinder.largestInscribed(firsts, lasts)
		assertNotNull(box)
		box!!
		// Every row of the rectangle lies inside the oval.
		for (y in box.top until box.bottom) {
			assertTrue("row $y leaves the bubble", box.left >= firsts[y] && box.right - 1 <= lasts[y])
		}
		// Much larger than a sliver, smaller than the 60x30 bounds (the ideal is about 42x21).
		val area = box.width * box.height
		assertTrue("unexpected area $area for $box", area in 700 until 1800)
	}

	@Test
	fun inscribedRectangleOfARectangularBubbleIsTheWholeBubble() {
		val firsts = IntArray(10) { 2 }
		val lasts = IntArray(10) { 17 }
		assertEquals(Box(2, 0, 18, 10), BubbleFinder.largestInscribed(firsts, lasts))
		assertNull(BubbleFinder.largestInscribed(IntArray(0), IntArray(0)))
		assertNull(BubbleFinder.largestInscribed(intArrayOf(-1, -1), intArrayOf(-1, -1)))
	}

	@Test
	fun rowSpansIgnoreGlyphHolesInsideTheBubble() {
		val p = bubbleWindow()
		val region = BubbleFinder.interior(p, 40, 40, BubbleFinder.seedsAround(Box(17, 18, 23, 22), 2), white)!!
		val (firsts, lasts) = BubbleFinder.rowSpans(region)
		// Row 19 of the window crosses the "text" block, yet its span still covers the full interior width.
		assertEquals(0, firsts[19 - region.bounds.top])
		assertEquals(region.bounds.width - 1, lasts[19 - region.bounds.top])
	}
}
