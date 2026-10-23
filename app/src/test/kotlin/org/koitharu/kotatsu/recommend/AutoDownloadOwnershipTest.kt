package org.koitharu.kotatsu.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadOwnershipTest {

	private val mb = 1024L * 1024L

	private fun manga(id: Long, chapters: List<Long>, mb: Long = 10, measured: Boolean = false) = AutoDownloadEntry(
		kind = AutoDownloadEntry.KIND_MANGA,
		title = "m$id",
		mangaId = id,
		chapterIds = chapters,
		bytes = mb * this.mb,
		isMeasured = measured,
		createdAt = 1_000L,
	)

	private fun book(path: String, mb: Long) = AutoDownloadEntry(
		kind = AutoDownloadEntry.KIND_BOOK,
		title = path,
		filePath = path,
		bytes = mb * this.mb,
		isMeasured = true,
		createdAt = 1_000L,
	)

	@Test
	fun ownsOnlyTheExactSilentWeQueued() {
		val entry = manga(1, listOf(11, 12, 13))
		assertTrue(AutoDownloadOwnership.ownsWork(entry, 1, listOf(13L, 11L, 12L), true))
		// Manual download of the same title: not silent.
		assertFalse(AutoDownloadOwnership.ownsWork(entry, 1, listOf(11L, 12L, 13L), false))
		// Manual download of other (or only some) chapters.
		assertFalse(AutoDownloadOwnership.ownsWork(entry, 1, listOf(11L, 12L), true))
		assertFalse(AutoDownloadOwnership.ownsWork(entry, 1, listOf(11L, 12L, 13L, 14L), true))
		// Whole-title manual download has no chapter list.
		assertFalse(AutoDownloadOwnership.ownsWork(entry, 1, null, true))
		assertFalse(AutoDownloadOwnership.ownsWork(entry, 1, emptyList(), true))
		// Another title, and books are never work.
		assertFalse(AutoDownloadOwnership.ownsWork(entry, 2, listOf(11L, 12L, 13L), true))
		assertFalse(AutoDownloadOwnership.ownsWork(book("/a.epub", 1), 0, listOf(1L), true))
	}

	@Test
	fun neverDeletesChaptersWeDidNotQueue() {
		assertNull(AutoDownloadOwnership.chaptersToDelete(listOf(1L, 2L), setOf(1L, 2L, 3L)))
		assertNull(AutoDownloadOwnership.chaptersToDelete(emptyList(), setOf(1L)))
		assertEquals(setOf(1L), AutoDownloadOwnership.chaptersToDelete(listOf(1L, 9L), setOf(1L, 2L)))
		assertEquals(emptySet<Long>(), AutoDownloadOwnership.chaptersToDelete(listOf(9L), setOf(1L)))
	}

	@Test
	fun existingOrOpenedBookIsNotDiscarded() {
		val start = 100_000L
		assertTrue(AutoDownloadOwnership.isFreshlyCreated(start + 5, start))
		assertTrue(AutoDownloadOwnership.isFreshlyCreated(start - 1_000, start)) // fs timestamp rounding
		assertFalse(AutoDownloadOwnership.isFreshlyCreated(start - 60_000, start))
		assertTrue(AutoDownloadOwnership.mayDiscardBook(start + 5, start, isOpened = false))
		assertFalse(AutoDownloadOwnership.mayDiscardBook(start + 5, start, isOpened = true))
		assertFalse(AutoDownloadOwnership.mayDiscardBook(start - 60_000, start, isOpened = false))
	}

	@Test
	fun inFlightMangaCountsAtLeastItsEstimate() {
		val hour = 3_600_000L
		// Half-downloaded: the estimate still counts.
		assertEquals(30 * mb, AutoDownloadOwnership.accountedBytes(5 * mb, 30 * mb, false, hour))
		// Larger than estimated: the real size counts straight away.
		assertEquals(50 * mb, AutoDownloadOwnership.accountedBytes(50 * mb, 30 * mb, false, hour))
		// Measured or old: the real size wins, even if smaller.
		assertEquals(5 * mb, AutoDownloadOwnership.accountedBytes(5 * mb, 30 * mb, true, hour))
		assertEquals(5 * mb, AutoDownloadOwnership.accountedBytes(5 * mb, 30 * mb, false, 3 * 24 * hour))
		assertTrue(AutoDownloadOwnership.isStillInFlight(false, hour))
		assertFalse(AutoDownloadOwnership.isStillInFlight(true, hour))
	}

	@Test
	fun reconcileKeepsConcurrentAdditionsAndRemovals() {
		val a = manga(1, listOf(11))
		val b = manga(2, listOf(21))
		val added = manga(3, listOf(31)) // added while reconcile was measuring
		val results = mapOf(a.key to a.copy(bytes = 7 * mb, isMeasured = true), b.key to null)
		// b was removed meanwhile by someone else; a gets measured; added is untouched.
		val merged = AutoDownloadOwnership.mergeReconciled(listOf(a, added), results)
		assertEquals(listOf(a.copy(bytes = 7 * mb, isMeasured = true), added), merged)
		// An entry reconcile wants to forget is dropped; one removed meanwhile is not resurrected.
		assertEquals(listOf(added), AutoDownloadOwnership.mergeReconciled(listOf(b, added), results))
		assertEquals(listOf(added), AutoDownloadOwnership.mergeReconciled(listOf(added), results))
	}

	@Test
	fun budgetNeverAdmitsMoreThanRemaining() {
		val entries = listOf(manga(1, listOf(11), 400), book("/a.epub", 100))
		assertEquals(524 * mb, AutoDownloadPlanner.remainingBudget(1024 * mb, entries))
		assertEquals(0L, AutoDownloadPlanner.remainingBudget(300 * mb, entries)) // protected content above cap
		assertEquals(0, AutoDownloadPlanner.chaptersWithinBudget(3, 20 * mb, 0L))
		assertEquals(2, AutoDownloadPlanner.chaptersWithinBudget(5, 20 * mb, 50 * mb))
		val fits = AutoDownloadPlanner.fitToBudget(listOf(30L, 80L, 20L), 60 * mb) { it * mb }
		assertEquals(listOf(30L, 20L), fits)
	}

	@Test
	fun evictionSkipsOpenedAndStopsWhenUnderLimit() {
		val old = manga(1, listOf(11), 300).copy(createdAt = 1)
		val opened = manga(2, listOf(21), 300).copy(createdAt = 2)
		val newer = manga(3, listOf(31), 300).copy(createdAt = 3)
		val victims = AutoDownloadPlanner.selectForEviction(listOf(old, opened, newer), 700 * mb) { it.mangaId == 2L }
		assertEquals(listOf(old), victims)
	}
}
