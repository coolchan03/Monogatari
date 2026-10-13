package org.koitharu.kotatsu.reader.translate

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Pure geometry and text helpers for drawing translated text onto a manga page (no Android types, unit-tested). */
internal object TranslationLayout {

	data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
		val width: Int get() = right - left
		val height: Int get() = bottom - top
	}

	/** Grows a box by [pad] pixels on every side, clamped to the image. */
	fun inflate(box: Box, pad: Int, imageWidth: Int, imageHeight: Int): Box = Box(
		left = (box.left - pad).coerceAtLeast(0),
		top = (box.top - pad).coerceAtLeast(0),
		right = (box.right + pad).coerceAtMost(imageWidth),
		bottom = (box.bottom + pad).coerceAtMost(imageHeight),
	)

	/**
	 * Japanese and Chinese bubbles are often tall, narrow columns of vertical text. English needs width, so a box
	 * much taller than wide is widened around its centre (never beyond 90% of the page).
	 */
	fun widenForHorizontalText(box: Box, imageWidth: Int, imageHeight: Int): Box {
		if (box.width <= 0 || box.height <= 0) return box
		if (box.height < box.width * 1.6f) return box
		val targetWidth = min(
			(imageWidth * 0.9f).roundToInt(),
			max(box.width, (box.height * 0.75f).roundToInt()),
		)
		val centre = (box.left + box.right) / 2
		var left = centre - targetWidth / 2
		var right = left + targetWidth
		if (left < 0) {
			right -= left
			left = 0
		}
		if (right > imageWidth) {
			left -= right - imageWidth
			right = imageWidth
		}
		return Box(left.coerceAtLeast(0), box.top, right, box.bottom.coerceAtMost(imageHeight))
	}

	/** Joins OCR lines: CJK scripts have no spaces between lines, others do. Hyphenated line breaks are re-joined. */
	fun joinLines(lines: List<String>, isCjk: Boolean): String {
		val cleaned = lines.map { it.trim() }.filter { it.isNotEmpty() }
		if (isCjk) return cleaned.joinToString("")
		val out = StringBuilder()
		for (line in cleaned) {
			if (out.isEmpty()) {
				out.append(line)
			} else if (out.endsWith('-') && line.firstOrNull()?.isLowerCase() == true) {
				out.setLength(out.length - 1)
				out.append(line)
			} else {
				out.append(' ').append(line)
			}
		}
		return out.toString()
	}

	/** True if the text is worth translating: it has letters/ideographs, not just numbers, dots or sound marks. */
	fun isMeaningful(text: String): Boolean = text.any { it.isLetter() }

	/** Black or white, whichever reads better on [argb]. */
	fun contrastingTextColor(argb: Int): Int {
		val r = (argb shr 16) and 0xFF
		val g = (argb shr 8) and 0xFF
		val b = argb and 0xFF
		val luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
		return if (luminance > 0.55) 0xFF111111.toInt() else 0xFFF5F5F5.toInt()
	}

	/** Median of each channel: robust background colour estimate from samples around a text box. */
	fun medianColor(samples: IntArray): Int {
		if (samples.isEmpty()) return 0xFFFFFFFF.toInt()
		fun channel(shift: Int): Int {
			val values = IntArray(samples.size) { (samples[it] shr shift) and 0xFF }
			values.sort()
			return values[values.size / 2]
		}
		return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
	}

	/**
	 * Largest text size (between [min] and [max]) for which [measureHeight] fits [availableHeight]. Binary search, so
	 * the expensive text layout is measured only a handful of times.
	 */
	fun fitTextSize(min: Float, max: Float, availableHeight: Int, measureHeight: (Float) -> Int): Float {
		if (measureHeight(max) <= availableHeight) return max
		var lo = min
		var hi = max
		repeat(8) {
			val mid = (lo + hi) / 2f
			if (measureHeight(mid) <= availableHeight) lo = mid else hi = mid
		}
		return lo
	}
}
