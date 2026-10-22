package org.koitharu.kotatsu.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadPlannerTest {

	private val mb = 1024L * 1024L

	private fun manga(id: Long, mb: Long, createdAt: Long, chapters: Int = 1, measured: Boolean = true) = AutoDownloadEntry(
		kind = AutoDownloadEntry.KIND_MANGA,
		title = "m$id",
		mangaId = id,
		chapterIds = (1..chapters).map { id * 100 + it },
		bytes = mb * this.mb,
		isMeasured = measured,
		createdAt = createdAt,
	)

	@Test
	fun pickChaptersStartsAfterCurrent() {
		val ids = listOf(1L, 2L, 3L, 4L, 5L, 6L)
		assertEquals(listOf(4L, 5L, 6L), AutoDownloadPlanner.pickChapters(ids, 3L, emptySet(), 3))
		assertEquals(listOf(4L, 5L), AutoDownloadPlanner.pickChapters(ids, 3L, emptySet(), 2))
	}

	@Test
	fun pickChaptersFromStartWithoutHistory() {
		val ids = listOf(10L, 20L, 30L)
		assertEquals(listOf(10L), AutoDownloadPlanner.pickChapters(ids, null, emptySet(), 1))
		// An unknown current chapter (other branch) also starts from the beginning.
		assertEquals(listOf(10L, 20L), AutoDownloadPlanner.pickChapters(ids, 99L, emptySet(), 2))
	}

	@Test
	fun pickChaptersSkipsExcludedAndEnd() {
		val ids = listOf(1L, 2L, 3L, 4L)
		assertEquals(listOf(2L, 4L), AutoDownloadPlanner.pickChapters(ids, 1L, setOf(3L), 5))
		assertTrue(AutoDownloadPlanner.pickChapters(ids, 4L, emptySet(), 3).isEmpty())
		assertTrue(AutoDownloadPlanner.pickChapters(ids, null, emptySet(), 0).isEmpty())
	}

	@Test
	fun chaptersWithinBudget() {
		assertEquals(3, AutoDownloadPlanner.chaptersWithinBudget(3, 10 * mb, 100 * mb))
		assertEquals(2, AutoDownloadPlanner.chaptersWithinBudget(5, 10 * mb, 25 * mb))
		assertEquals(0, AutoDownloadPlanner.chaptersWithinBudget(5, 10 * mb, 9 * mb))
		assertEquals(0, AutoDownloadPlanner.chaptersWithinBudget(5, 10 * mb, 0))
	}

	@Test
	fun fitToBudgetKeepsOrderAndSkipsTooLarge() {
		val sizes = listOf(400L, 700L, 300L, 200L)
		val picked = AutoDownloadPlanner.fitToBudget(sizes, 1000L) { it }
		assertEquals(listOf(400L, 300L, 200L), picked)
		assertTrue(AutoDownloadPlanner.fitToBudget(sizes, 100L) { it }.isEmpty())
	}

	@Test
	fun remainingBudgetNeverNegative() {
		val entries = listOf(manga(1, 600, 0), manga(2, 600, 0))
		assertEquals(0L, AutoDownloadPlanner.remainingBudget(1000 * mb, entries))
		assertEquals(800 * mb, AutoDownloadPlanner.remainingBudget(2000 * mb, entries))
	}

	@Test
	fun estimateChapterBytesUsesMeasuredEntries() {
		assertEquals(AutoDownloadPlanner.DEFAULT_CHAPTER_BYTES, AutoDownloadPlanner.estimateChapterBytes(emptyList()))
		val entries = listOf(
			manga(1, 30, 0, chapters = 3),
			manga(2, 10, 0, chapters = 1),
			manga(3, 900, 0, chapters = 1, measured = false), // estimates are ignored
		)
		assertEquals(10 * mb, AutoDownloadPlanner.estimateChapterBytes(entries))
	}

	@Test
	fun evictionOrderIsOldestUnopenedFirst() {
		val a = manga(1, 100, createdAt = 300)
		val b = manga(2, 100, createdAt = 100)
		val c = manga(3, 100, createdAt = 200)
		val opened = setOf(b.key)
		assertEquals(listOf(c, a), AutoDownloadPlanner.evictionOrder(listOf(a, b, c)) { it.key in opened })
	}

	@Test
	fun selectForEvictionStopsUnderLimit() {
		val a = manga(1, 400, createdAt = 1)
		val b = manga(2, 400, createdAt = 2)
		val c = manga(3, 400, createdAt = 3)
		val entries = listOf(c, a, b)
		assertTrue(AutoDownloadPlanner.selectForEviction(entries, 1200 * mb) { false }.isEmpty())
		assertEquals(listOf(a), AutoDownloadPlanner.selectForEviction(entries, 1000 * mb) { false })
		assertEquals(listOf(a, b), AutoDownloadPlanner.selectForEviction(entries, 500 * mb) { false })
	}

	@Test
	fun selectForEvictionNeverTouchesOpened() {
		val a = manga(1, 400, createdAt = 1)
		val b = manga(2, 400, createdAt = 2)
		val result = AutoDownloadPlanner.selectForEviction(listOf(a, b), 100 * mb) { it.key == a.key }
		assertEquals(listOf(b), result)
	}

	@Test
	fun bookAndMangaKeysDiffer() {
		val book = AutoDownloadEntry(
			kind = AutoDownloadEntry.KIND_BOOK,
			title = "b",
			filePath = "/x/book.epub",
			bytes = 1,
			createdAt = 0,
		)
		assertEquals("book:/x/book.epub", book.key)
		assertEquals("manga:1", manga(1, 1, 0).key)
	}
}
