package org.koitharu.kotatsu.details.domain

import org.koitharu.kotatsu.parsers.model.MangaChapter
import kotlin.math.floor

/**
 * Finds whole-number chapters that are absent from a numbered chapter list.
 *
 * It is deliberately conservative: a false "missing" warning is worse than a missed one, so any list whose
 * numbering does not look like one plain ascending run reports nothing.
 *
 *  - chapters without a usable number (<= 0, NaN) are ignored, so specials and unnumbered extras never count;
 *  - decimals and parts (10.5, 10.1) count as present for their whole number (10), so split releases are not flagged;
 *  - duplicates (several scanlation groups, re-uploads) collapse into one;
 *  - numbering that restarts per volume (the same number under different volumes) is treated as irregular;
 *  - a range that is mostly empty, absurdly wide (year-style numbers) or has a very long run of absent numbers is
 *    treated as renumbering rather than missing chapters.
 */
object ChapterGapDetector {

	/** Fewer numbered chapters than this gives too little evidence about the numbering scheme. */
	const val MIN_NUMBERED_CHAPTERS = 3

	/** A run longer than this is much more likely a renumbering (season/arc offset) than that many lost chapters. */
	const val MAX_RUN_LENGTH = 30

	private const val MAX_NUMBER_SPAN = 5000

	/** An inclusive run of absent whole chapter numbers. */
	data class Gap(val first: Int, val last: Int) {

		val size: Int
			get() = last - first + 1

		override fun toString(): String = if (first == last) first.toString() else "$first–$last"
	}

	fun findGaps(chapters: Collection<MangaChapter>): List<Gap> {
		val numbered = chapters.filter { it.number > 0f && it.number.isFinite() }
		if (numbered.size < MIN_NUMBERED_CHAPTERS) {
			return emptyList()
		}
		val volumesByNumber = HashMap<Int, MutableSet<Int>>()
		val present = HashSet<Int>()
		for (chapter in numbered) {
			val whole = floor(chapter.number).toInt()
			if (whole < 1) {
				continue // 0.5 and similar: a prologue-style extra, not part of the 1..N run
			}
			present += whole
			if (chapter.volume > 0) {
				volumesByNumber.getOrPut(whole) { HashSet() } += chapter.volume
			}
		}
		if (present.size < MIN_NUMBERED_CHAPTERS) {
			return emptyList()
		}
		// The same chapter number under two different volumes means numbering restarts per volume.
		if (volumesByNumber.values.any { it.size > 1 }) {
			return emptyList()
		}
		val min = present.min()
		val max = present.max()
		val span = max - min + 1
		if (span > MAX_NUMBER_SPAN || span == present.size) {
			return emptyList() // year-style numbers, or nothing absent at all
		}
		val missingCount = span - present.size
		if (missingCount * 2 > span) {
			return emptyList() // mostly empty: irregular numbering, not lost chapters
		}
		val gaps = ArrayList<Gap>()
		var runStart = -1
		for (n in min..max + 1) {
			val absent = n <= max && n !in present
			if (absent && runStart == -1) {
				runStart = n
			} else if (!absent && runStart != -1) {
				val gap = Gap(runStart, n - 1)
				if (gap.size <= MAX_RUN_LENGTH) {
					gaps += gap
				}
				runStart = -1
			}
		}
		return gaps
	}

	/** Short human-readable list such as "12–14, 20, 31", ending in an ellipsis when there are more than [maxRuns]. */
	fun format(gaps: List<Gap>, maxRuns: Int = 6): String {
		val shown = gaps.take(maxRuns).joinToString(", ")
		return if (gaps.size > maxRuns) "$shown, …" else shown
	}

	fun missingCount(gaps: List<Gap>): Int = gaps.sumOf { it.size }
}
