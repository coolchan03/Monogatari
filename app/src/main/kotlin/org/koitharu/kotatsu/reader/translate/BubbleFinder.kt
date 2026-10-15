package org.koitharu.kotatsu.reader.translate

/**
 * Finds the empty interior of a speech bubble around recognised text by flood-filling pixels close to the bubble's
 * paper colour, so translated text can use the whole bubble instead of only the old text's footprint (pure, unit-tested).
 */
internal object BubbleFinder {

	/** Sum of per-channel differences below which two colours count as the same paper. */
	const val DEFAULT_TOLERANCE = 72

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
	): TranslationLayout.Box? {
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
		return TranslationLayout.Box(minX, minY, maxX + 1, maxY + 1)
	}

	/** Seeds around [box] (window coordinates): the middle and corners of each side, just outside its edge. */
	fun seedsAround(box: TranslationLayout.Box, margin: Int): List<Pair<Int, Int>> {
		val l = box.left - margin; val r = box.right + margin - 1
		val t = box.top - margin; val b = box.bottom + margin - 1
		val cx = (box.left + box.right) / 2; val cy = (box.top + box.bottom) / 2
		return listOf(l to cy, r to cy, cx to t, cx to b, l to t, r to t, l to b, r to b)
	}
}
