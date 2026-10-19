package org.koitharu.kotatsu.core.media

import kotlin.math.roundToInt

/**
 * Scroll position of a reflowing text reader as a fraction of its scrollable range, so the reader returns to the same
 * place after rotation, a fold/unfold, split-screen or a freeform window resize; a pixel offset does not survive the
 * text re-wrapping at a new width.
 */
object ReadingScrollPosition {

	fun fraction(scrollY: Int, contentHeight: Int, viewportHeight: Int): Float {
		val range = contentHeight - viewportHeight
		if (range <= 0) return 0f
		return (scrollY.toFloat() / range).coerceIn(0f, 1f)
	}

	fun offset(fraction: Float, contentHeight: Int, viewportHeight: Int): Int {
		val range = contentHeight - viewportHeight
		if (range <= 0 || fraction.isNaN()) return 0
		return (fraction.coerceIn(0f, 1f) * range).roundToInt()
	}
}
