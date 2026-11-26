package org.koitharu.kotatsu.details.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.details.domain.ChapterGapDetector.Gap
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.core.model.LocalMangaSource

class ChapterGapDetectorTest {

	private var nextId = 1L

	private fun ch(number: Float, volume: Int = 0, scanlator: String? = null) = MangaChapter(
		id = nextId++,
		title = null,
		number = number,
		volume = volume,
		url = "/c/$nextId",
		scanlator = scanlator,
		uploadDate = 0L,
		branch = null,
		source = LocalMangaSource,
	)

	private fun list(vararg numbers: Number, volume: Int = 0) = numbers.map { ch(it.toFloat(), volume) }

	@Test
	fun completeRunHasNoGaps() {
		assertTrue(ChapterGapDetector.findGaps(list(1, 2, 3, 4, 5)).isEmpty())
	}

	@Test
	fun singleAndRangeGapsAreReported() {
		val gaps = ChapterGapDetector.findGaps(list(1, 2, 5, 6, 8, 9, 10))
		assertEquals(listOf(Gap(3, 4), Gap(7, 7)), gaps)
		assertEquals(3, ChapterGapDetector.missingCount(gaps))
		assertEquals("3–4, 7", ChapterGapDetector.format(gaps))
	}

	@Test
	fun orderDoesNotMatter() {
		assertEquals(listOf(Gap(4, 4)), ChapterGapDetector.findGaps(list(6, 1, 5, 2, 3)))
	}

	@Test
	fun seriesNeedNotStartAtOne() {
		assertTrue(ChapterGapDetector.findGaps(list(45, 46, 47, 48)).isEmpty())
		assertEquals(listOf(Gap(47, 47)), ChapterGapDetector.findGaps(list(45, 46, 48, 49)))
	}

	@Test
	fun duplicatesFromSeveralGroupsDoNotHideOrInventGaps() {
		val chapters = listOf(
			ch(1f, scanlator = "A"), ch(1f, scanlator = "B"),
			ch(2f, scanlator = "A"), ch(2f, scanlator = "B"),
			ch(4f, scanlator = "A"), ch(4f, scanlator = "B"),
		)
		assertEquals(listOf(Gap(3, 3)), ChapterGapDetector.findGaps(chapters))
	}

	@Test
	fun decimalAndPartChaptersCountAsTheirWholeNumber() {
		// 10.5 and 11.1/11.2 are extras or parts: they never produce a gap by themselves ...
		assertTrue(ChapterGapDetector.findGaps(list(9, 10, 10.5f, 11.1f, 11.2f, 12)).isEmpty())
		// ... but a real hole next to them is still found.
		assertEquals(listOf(Gap(11, 11)), ChapterGapDetector.findGaps(list(9, 10, 10.5f, 12, 13)))
	}

	@Test
	fun unnumberedAndZeroChaptersAreIgnored() {
		val chapters = list(0, 0, 0.5f, 1, 2, 3, -1, Float.NaN)
		assertTrue(ChapterGapDetector.findGaps(chapters).isEmpty())
	}

	@Test
	fun tooFewNumberedChaptersGiveNoVerdict() {
		assertTrue(ChapterGapDetector.findGaps(list(1, 10)).isEmpty())
		assertTrue(ChapterGapDetector.findGaps(emptyList()).isEmpty())
	}

	@Test
	fun numberingThatRestartsPerVolumeIsNotReportedAsMissing() {
		val chapters = list(1, 2, 3, volume = 1) + list(1, 2, 3, volume = 2) + list(1, 3, volume = 3)
		assertTrue(ChapterGapDetector.findGaps(chapters).isEmpty())
	}

	@Test
	fun continuousNumberingAcrossVolumesStillWorks() {
		val chapters = list(1, 2, 4, volume = 1) + list(5, 6, 8, volume = 2)
		assertEquals(listOf(Gap(3, 3), Gap(7, 7)), ChapterGapDetector.findGaps(chapters))
	}

	@Test
	fun hugeJumpsAreTreatedAsRenumberingNotMissingChapters() {
		// 1..5 then 100..104: a 94-chapter run is an arc/season offset, not 94 lost chapters.
		assertTrue(ChapterGapDetector.findGaps(list(1, 2, 3, 4, 5, 100, 101, 102, 103, 104)).isEmpty())
		// Year-style numbering.
		assertTrue(ChapterGapDetector.findGaps(list(20240101, 20240215, 20240301)).isEmpty())
	}

	@Test
	fun mostlyEmptyRangeIsIrregular() {
		assertTrue(ChapterGapDetector.findGaps(list(1, 5, 9, 13)).isEmpty())
	}

	@Test
	fun longRunIsDroppedButShortOnesAroundItAreKept() {
		// 51..89 is a 39-chapter run (dropped as a renumbering); 121 is a lone hole (kept).
		val numbers = (1..50) + (90..120) + listOf(122)
		val gaps = ChapterGapDetector.findGaps(numbers.map { ch(it.toFloat()) })
		assertEquals(listOf(Gap(121, 121)), gaps)
	}

	@Test
	fun formatTruncatesLongLists() {
		val gaps = (1..8).map { Gap(it * 3, it * 3) }
		assertEquals("3, 6, 9, 12, 15, 18, …", ChapterGapDetector.format(gaps, maxRuns = 6))
	}
}
