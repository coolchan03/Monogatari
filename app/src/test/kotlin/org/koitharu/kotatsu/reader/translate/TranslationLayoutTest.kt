package org.koitharu.kotatsu.reader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.reader.translate.TranslationLayout.Box

class TranslationLayoutTest {

	@Test
	fun tallVerticalBubbleIsWidenedAroundItsCentre() {
		val box = Box(left = 480, top = 100, right = 520, bottom = 400) // 40 x 300 column
		val widened = TranslationLayout.widenForHorizontalText(box, imageWidth = 1000, imageHeight = 1500)
		assertEquals(225, widened.width)
		assertEquals(500, (widened.left + widened.right) / 2)
		assertEquals(box.top, widened.top)
		assertEquals(box.bottom, widened.bottom)
	}

	@Test
	fun widenedBoxStaysInsideThePage() {
		val box = Box(left = 0, top = 0, right = 30, bottom = 400)
		val widened = TranslationLayout.widenForHorizontalText(box, imageWidth = 200, imageHeight = 400)
		assertEquals(0, widened.left)
		assertTrue(widened.right <= 200)
		assertEquals(180, widened.width) // capped at 90% of the page width
	}

	@Test
	fun wideBoxesAreLeftAlone() {
		val box = Box(10, 10, 210, 60)
		assertEquals(box, TranslationLayout.widenForHorizontalText(box, 1000, 1000))
	}

	@Test
	fun joinsLines() {
		assertEquals("こんにちは世界", TranslationLayout.joinLines(listOf("こんにちは", " 世界 "), isCjk = true))
		assertEquals("I really love this", TranslationLayout.joinLines(listOf("I really", "love this"), isCjk = false))
		assertEquals("wonderful day", TranslationLayout.joinLines(listOf("wonder-", "ful day"), isCjk = false))
	}

	@Test
	fun meaningfulText() {
		assertTrue(TranslationLayout.isMeaningful("どうして"))
		assertTrue(TranslationLayout.isMeaningful("Why?!"))
		assertFalse(TranslationLayout.isMeaningful("12 ... !!"))
	}

	@Test
	fun colours() {
		assertEquals(0xFF111111.toInt(), TranslationLayout.contrastingTextColor(0xFFFFFFFF.toInt()))
		assertEquals(0xFFF5F5F5.toInt(), TranslationLayout.contrastingTextColor(0xFF101010.toInt()))
		val white = 0xFFFFFFFF.toInt()
		val black = 0xFF000000.toInt()
		assertEquals(white, TranslationLayout.medianColor(intArrayOf(white, white, black, white, 0xFF808080.toInt())))
	}

	@Test
	fun fitsLargestSizeThatFits() {
		// Pretend height grows linearly with text size: 10px of height per 1sp.
		val size = TranslationLayout.fitTextSize(8f, 64f, availableHeight = 300) { (it * 10).toInt() }
		assertTrue(size in 29f..31f && (size * 10).toInt() <= 300)
		assertEquals(64f, TranslationLayout.fitTextSize(8f, 64f, availableHeight = 1000) { (it * 10).toInt() })
	}
}
