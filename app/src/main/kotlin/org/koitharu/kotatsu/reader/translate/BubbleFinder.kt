package org.koitharu.kotatsu.reader.translate

/**
 * Finds the empty interior of a speech bubble around recognised text by flood-filling pixels close to the bubble's
 * paper colour, so translated text can use the whole bubble instead of only the old text's footprint (pure, unit-tested).
 */
internal object BubbleFinder {

	/** Sum of per-channel differences below which two colours count as the same paper. */
	const val DEFAULT_TOLERANCE = 72

	/** The actual connected bubble interior: unlike its bounds, this mask excludes panel art and bubble corners. */
	data class Region(val bounds: TranslationLayout.Box, val width: Int, val height: Int, val mask: BooleanArray) {
		fun contains(x: Int, y: Int): Boolean =
			x in 0 until width && y in 0 until height && mask[y * width + x]
	}

	private fun close(a: Int, b: Int, tolerance: Int): Boolean {
		val dr = ((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)
		val dg = ((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)
		val db = (a and 0xFF) - (b and 0xFF)
		return kotlin.math.abs(dr) + kotlin.math.abs(dg) + kotlin.math.abs(db) <= tolerance
	}

	/**
	 * @param pixels ARGB pixels of a window around the text, row by row ([width] x [height])
	 * @param seeds window coordinates (x to y) known to lie inside the bubble, near the text
	 * @return the bounding box of the connected paper-coloured area in window coordinates, or null when the fill
	 * leaks out to two or more window edges (borderless text on a white page: nothing bubble-like to grow into)
	 */
	fun interiorBounds(
		pixels: IntArray, width: Int, height: Int, seeds: List<Pair<Int, Int>>, paper: Int,
		tolerance: Int = DEFAULT_TOLERANCE,
	): TranslationLayout.Box? = interior(pixels, width, height, seeds, paper, tolerance)?.bounds

	/** Preserve a pixel-accurate interior so filling never paints over the outline or artwork in a corner. */
	fun interior(
		pixels: IntArray, width: Int, height: Int, seeds: List<Pair<Int, Int>>, paper: Int,
		tolerance: Int = DEFAULT_TOLERANCE,
	): Region? {
		if (width <= 0 || height <= 0 || pixels.size < width * height) return null
		val visited = BooleanArray(width * height)
		val queue = IntArray(width * height)
		var head = 0
		var tail = 0
		for ((sx, sy) in seeds) {
			if (sx !in 0 until width || sy !in 0 until height) continue
			val index = sy * width + sx
			if (!visited[index] && close(pixels[index], paper, tolerance)) {
				visited[index] = true
				queue[tail++] = index
			}
		}
		if (tail == 0) return null
		var minX = width; var minY = height; var maxX = -1; var maxY = -1
		while (head < tail) {
			val index = queue[head++]
			val x = index % width
			val y = index / width
			if (x < minX) minX = x
			if (x > maxX) maxX = x
			if (y < minY) minY = y
			if (y > maxY) maxY = y
			for (n in intArrayOf(if (x > 0) index - 1 else -1, if (x < width - 1) index + 1 else -1,
				if (y > 0) index - width else -1, if (y < height - 1) index + width else -1)) {
				if (n >= 0 && !visited[n] && close(pixels[n], paper, tolerance)) {
					visited[n] = true
					queue[tail++] = n
				}
			}
		}
		val touching = listOf(minX == 0, maxX == width - 1, minY == 0, maxY == height - 1).count { it }
		if (touching >= 2) return null
		return Region(TranslationLayout.Box(minX, minY, maxX + 1, maxY + 1), width, height, visited)
	}

	/**
	 * Largest axis-aligned rectangle inside a bubble whose rows span `firsts[y]..lasts[y]` (inclusive, -1 = empty row).
	 * Round and oval bubbles are much narrower near the top and bottom than their bounding box, so text laid out in
	 * the bounds would be cut by the outline; this is the area text can actually use. Rows are relative to the region.
	 */
	fun largestInscribed(firsts: IntArray, lasts: IntArray): TranslationLayout.Box? {
		val rows = minOf(firsts.size, lasts.size)
		if (rows == 0) return null
		// Big bubbles: try every few start rows; each scan is still exact for the rows it covers.
		val stride = (rows / 160).coerceAtLeast(1)
		var best: TranslationLayout.Box? = null
		var bestArea = 0L
		var top = 0
		while (top < rows) {
			var lo = Int.MIN_VALUE
			var hi = Int.MAX_VALUE
			for (bottom in top until rows) {
				if (firsts[bottom] < 0 || lasts[bottom] < firsts[bottom]) break
				lo = maxOf(lo, firsts[bottom])
				hi = minOf(hi, lasts[bottom])
				val width = hi - lo + 1
				if (width <= 0) break
				val area = width.toLong() * (bottom - top + 1)
				if (area > bestArea) {
					bestArea = area
					best = TranslationLayout.Box(lo, top, hi + 1, bottom + 1)
				}
			}
			top += stride
		}
		return best
	}

	/** Row spans of [region]'s mask inside its bounds, for [largestInscribed]; holes left by glyphs are ignored. */
	fun rowSpans(region: Region): Pair<IntArray, IntArray> {
		val b = region.bounds
		val firsts = IntArray(b.height) { -1 }
		val lasts = IntArray(b.height) { -1 }
		for (y in b.top until b.bottom) {
			for (x in b.left until b.right) {
				if (region.contains(x, y)) {
					if (firsts[y - b.top] < 0) firsts[y - b.top] = x - b.left
					lasts[y - b.top] = x - b.left
				}
			}
		}
		return firsts to lasts
	}

	/** Seeds around [box] (window coordinates): the middle and corners of each side, just outside its edge. */
	fun seedsAround(box: TranslationLayout.Box, margin: Int): List<Pair<Int, Int>> {
		val l = box.left - margin; val r = box.right + margin - 1
		val t = box.top - margin; val b = box.bottom + margin - 1
		val cx = (box.left + box.right) / 2; val cy = (box.top + box.bottom) / 2
		return listOf(l to cy, r to cy, cx to t, cx to b, l to t, r to t, l to b, r to b)
	}
}
