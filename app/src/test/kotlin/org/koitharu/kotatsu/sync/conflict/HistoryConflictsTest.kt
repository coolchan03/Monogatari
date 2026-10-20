package org.koitharu.kotatsu.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.sync.conflict.HistoryConflicts.Choice
import org.koitharu.kotatsu.sync.conflict.HistoryConflicts.Snapshot

class HistoryConflictsTest {

	private val lastSync = 1_000L

	private fun snap(id: Long, chapter: Long, page: Int, updated: Long, deleted: Long = 0L) =
		Snapshot(id, "Title $id", chapter, page, -1f, updated, deleted)

	@Test
	fun onlyTitlesChangedOnBothSidesAreConflicts() {
		val local = mapOf(
			1L to snap(1, 10, 0, 1_500),
			2L to snap(2, 20, 0, 500),
			3L to snap(3, 30, 4, 1_700),
		)
		val incoming = listOf(
			snap(1, 12, 0, 1_200), // both changed -> conflict
			snap(2, 22, 0, 1_300), // only incoming changed -> merges normally
			snap(3, 30, 4, 1_900), // same position -> nothing to ask
			snap(4, 40, 0, 1_900), // new on this device -> merges normally
		)
		val conflicts = HistoryConflicts.find(local, incoming, lastSync)
		assertEquals(listOf(1L), conflicts.map { it.mangaId })
		assertEquals(Choice.KEEP_LOCAL, conflicts.single().recommended)
	}

	@Test
	fun firstSyncAsksAboutEveryDifferenceAndRecommendsNewest() {
		val local = mapOf(1L to snap(1, 10, 0, 500), 2L to snap(2, 20, 3, 900))
		val incoming = listOf(snap(1, 11, 0, 800), snap(2, 20, 7, 100))
		val conflicts = HistoryConflicts.find(local, incoming, lastSyncAt = 0L).associateBy { it.mangaId }
		assertEquals(Choice.TAKE_INCOMING, conflicts.getValue(1).recommended)
		assertEquals(Choice.KEEP_LOCAL, conflicts.getValue(2).recommended)
	}

	@Test
	fun deletionIsAChangeAndWinsTies() {
		val local = mapOf(1L to snap(1, 10, 0, 2_000))
		val incoming = listOf(snap(1, 10, 0, 1_100, deleted = 2_000))
		val conflict = HistoryConflicts.find(local, incoming, lastSync).single()
		assertTrue(conflict.incoming.isDeleted)
		assertEquals(Choice.TAKE_INCOMING, conflict.recommended)
	}

	@Test
	fun resolveUsesChoicesThenRecommendation() {
		val local = mapOf(1L to snap(1, 10, 0, 1_500), 2L to snap(2, 20, 0, 1_100))
		val incoming = listOf(snap(1, 11, 0, 1_200), snap(2, 21, 0, 1_600))
		val conflicts = HistoryConflicts.find(local, incoming, lastSync)

		val newest = HistoryConflicts.resolve(conflicts, HistoryConflicts.newestForAll(conflicts))
		assertEquals(setOf(1L), newest.keepLocal)
		assertEquals(setOf(2L), newest.takeIncoming)

		val keepAll = HistoryConflicts.resolve(conflicts, HistoryConflicts.keepLocalForAll(conflicts))
		assertEquals(setOf(1L, 2L), keepAll.keepLocal)
		assertTrue(keepAll.takeIncoming.isEmpty())

		val mixed = HistoryConflicts.resolve(conflicts, mapOf(1L to Choice.TAKE_INCOMING))
		assertEquals(setOf(1L, 2L), mixed.takeIncoming)
	}

	@Test
	fun mostRecentlyTouchedConflictsComeFirst() {
		val local = mapOf(1L to snap(1, 10, 0, 1_100), 2L to snap(2, 20, 0, 5_000))
		val incoming = listOf(snap(1, 11, 0, 1_200), snap(2, 21, 0, 1_300))
		assertEquals(listOf(2L, 1L), HistoryConflicts.find(local, incoming, lastSync).map { it.mangaId })
	}

	@Test
	fun chapterNumbersFormatCompactly() {
		assertEquals("12", HistoryConflicts.formatChapterNumber(12f))
		assertEquals("12.5", HistoryConflicts.formatChapterNumber(12.5f))
		assertNull(HistoryConflicts.formatChapterNumber(0f))
		assertNull(HistoryConflicts.formatChapterNumber(null))
	}
}
