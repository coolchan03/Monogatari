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

	fun union(a: Box, b: Box) = Box(min(a.left, b.left), min(a.top, b.top), max(a.right, b.right), max(a.bottom, b.bottom))

	/** [box] pulled in by [n] pixels on every side (never inverted). */
	fun shrink(box: Box, n: Int): Box {
		val l = min(box.left + n, (box.left + box.right) / 2)
		val t = min(box.top + n, (box.top + box.bottom) / 2)
		return Box(l, t, max(box.right - n, l), max(box.bottom - n, t))
	}

	/** Per-channel linear blend of two opaque colours; [t] is 0 for [a] and 1 for [b]. */
	fun lerpColor(a: Int, b: Int, t: Float): Int {
		fun channel(shift: Int): Int {
			val x = (a shr shift) and 0xFF
			val y = (b shr shift) and 0xFF
			return (x + (y - x) * t.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)
		}
		return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
	}

	/**
	 * Background colour at position ([fx], [fy]), each 0..1 across a box, from the median colours sampled along its
	 * [left], [right], [top] and [bottom] edges: fills a text box so shaded bubbles and gradients stay smooth.
	 */
	fun blendEdges(left: Int, right: Int, top: Int, bottom: Int, fx: Float, fy: Float): Int =
		lerpColor(lerpColor(left, right, fx), lerpColor(top, bottom, fy), 0.5f)

	/** Largest difference in any colour channel between the given colours (0 = identical). */
	fun colorSpread(colors: IntArray): Int {
		var spread = 0
		for (shift in intArrayOf(16, 8, 0)) {
			val values = colors.map { (it shr shift) and 0xFF }
			spread = max(spread, (values.maxOrNull() ?: 0) - (values.minOrNull() ?: 0))
		}
		return spread
	}

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

	/** Use traditional right-to-left vertical columns only for CJK-target text in a tall bubble. */
	fun prefersVerticalText(language: String?, text: String, box: Box): Boolean {
		val tag = language?.substringBefore('-')?.lowercase()
		if (tag != "ja" && tag != "zh") return false
		if (box.width <= 0 || box.height < box.width * 1.3f) return false
		val visible = text.count { !it.isWhitespace() }
		if (visible == 0) return false
		val cjk = text.count { it in '\u3000'..'\u9fff' || it in '\uf900'..'\ufaff' }
		return cjk * 2 >= visible
	}

	/** Unicode RTL-script handling without forcing Latin/numeric titles into RTL. */
	fun isRtlLanguage(language: String?): Boolean =
		language?.substringBefore('-')?.lowercase() in setOf("ar", "he", "fa", "ur", "ps", "sd")

	/** An OCR region with its text, used for bubble grouping. */
	data class Piece(val box: Box, val text: String, val confidence: Float = -1f)

	/**
	 * Merges OCR regions that belong to the same speech bubble: boxes that overlap or are closer than [gapFactor] times
	 * the smaller box dimension. Text is joined in reading order (right-to-left columns for vertical CJK text).
	 */
	fun groupPieces(pieces: List<Piece>, isCjk: Boolean, gapFactor: Float = 0.6f): List<Piece> {
		val n = pieces.size
		if (n < 2) return pieces
		val parent = IntArray(n) { it }
		fun find(i: Int): Int {
			var r = i
			while (parent[r] != r) r = parent[r]
			var c = i
			while (parent[c] != r) { val next = parent[c]; parent[c] = r; c = next }
			return r
		}
		for (i in 0 until n) for (j in i + 1 until n) {
			if (isSameBubble(pieces[i].box, pieces[j].box, gapFactor)) parent[find(i)] = find(j)
		}
		return pieces.indices.groupBy { find(it) }.values.map { indices ->
			val members = indices.map { pieces[it] }
			if (members.size == 1) return@map members[0]
			val ordered = if (isCjk) {
				members.sortedWith(compareByDescending<Piece> { (it.box.left + it.box.right) / 2 }.thenBy { it.box.top })
			} else {
				members.sortedWith(compareBy<Piece> { it.box.top / 12 }.thenBy { it.box.left })
			}
			val union = Box(
				ordered.minOf { it.box.left }, ordered.minOf { it.box.top },
				ordered.maxOf { it.box.right }, ordered.maxOf { it.box.bottom },
			)
			val known = ordered.map { it.confidence }.filter { it >= 0f }
			Piece(
				union,
				joinLines(ordered.map { it.text }, isCjk),
				if (known.isEmpty()) -1f else known.min(),
			)
		}.sortedWith(compareBy({ it.box.top }, { it.box.left }))
	}

	private fun isSameBubble(a: Box, b: Box, gapFactor: Float): Boolean {
		val gapX = max(0, max(a.left, b.left) - min(a.right, b.right))
		val gapY = max(0, max(a.top, b.top) - min(a.bottom, b.bottom))
		val limit = (min(min(a.width, a.height), min(b.width, b.height)) * gapFactor).roundToInt()
		return gapX <= limit && gapY <= limit
	}

	/**
	 * Vertical growth needed for text that does not fit even at the smallest size: extends [box] down and up (within
	 * the page) by at most [maxGrowth] of its height.
	 */
	fun growToFit(box: Box, neededHeight: Int, imageHeight: Int, maxGrowth: Float = 0.5f): Box {
		val missing = neededHeight - box.height
		if (missing <= 0) return box
		val allowed = min(missing, (box.height * maxGrowth).roundToInt())
		val down = min(allowed - allowed / 2, imageHeight - box.bottom)
		val up = min(allowed - down, box.top)
		return Box(box.left, box.top - up, box.right, box.bottom + down)
	}
}
