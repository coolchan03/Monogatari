package org.koitharu.kotatsu.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.core.model.LocalMangaSource
import org.koitharu.kotatsu.parsers.model.MangaChapter

class ChapterNavigatorTest {

	private fun ch(id: Long, number: Float, branch: String?, volume: Int = 0) = MangaChapter(
		id = id, title = null, number = number, volume = volume, url = "u$id",
		scanlator = branch, uploadDate = 0L, branch = branch, source = LocalMangaSource,
	)

	// Two groups, laid out branch after branch like MangaDetails.allChapters
	private val twoGroups = listOf(
		ch(1, 1f, "A"), ch(2, 2f, "A"), ch(3, 3f, "A"),
		ch(11, 1f, "B"), ch(12, 2f, "B"), ch(13, 3f, "B"),
	)

	@Test
	fun staysInTheCurrentGroup() {
		assertEquals(3L, ChapterNavigator.target(twoGroups, 2, +1)?.id)
		assertEquals(1L, ChapterNavigator.target(twoGroups, 2, -1)?.id)
		assertEquals(12L, ChapterNavigator.target(twoGroups, 11, +1)?.id)
	}

	@Test
	fun endOfGroupDoesNotHopToTheNextGroup() {
		assertNull(ChapterNavigator.target(twoGroups, 3, +1))
		assertNull(ChapterNavigator.target(twoGroups, 11, -1))
	}

	@Test
	fun singleBranchTitleBehavesLikeBefore() {
		val list = (1L..4L).map { ch(it, it.toFloat(), null) }
		assertEquals(2L, ChapterNavigator.target(list, 1, +1)?.id)
		assertNull(ChapterNavigator.target(list, 4, +1))
		assertNull(ChapterNavigator.target(list, 1, -1))
	}

	@Test
	fun optingOutOfBranchPreferenceUsesTheFlatList() {
		val flat = ChapterNavigator.Options(preferSameBranch = false)
		assertEquals(11L, ChapterNavigator.target(twoGroups, 3, +1, flat)?.id)
	}

	@Test
	fun skipReadChapters() {
		val read = setOf(2L, 3L)
		val opts = ChapterNavigator.Options(skipRead = true)
		val list = (1L..5L).map { ch(it, it.toFloat(), null) }
		assertEquals(4L, ChapterNavigator.target(list, 1, +1, opts) { it.id in read }?.id)
		// everything after is read -> nothing to go to
		assertNull(ChapterNavigator.target(list, 1, +1, opts) { it.id > 1 })
		// default ignores read state
		assertEquals(2L, ChapterNavigator.target(list, 1, +1) { it.id in read }?.id)
	}

	@Test
	fun skipDuplicateNumbers() {
		val list = listOf(ch(1, 1f, null), ch(2, 2f, null), ch(3, 2f, null), ch(4, 3f, null))
		val opts = ChapterNavigator.Options(skipDuplicateNumbers = true)
		assertEquals(4L, ChapterNavigator.target(list, 2, +1, opts)?.id)
		assertEquals(3L, ChapterNavigator.target(list, 2, +1)?.id)
		// unnumbered chapters (0) are never treated as duplicates of each other
		val unnumbered = listOf(ch(1, 0f, null), ch(2, 0f, null))
		assertEquals(2L, ChapterNavigator.target(unnumbered, 1, +1, opts)?.id)
	}

	@Test
	fun unknownCurrentAndZeroDelta() {
		assertNull(ChapterNavigator.target(twoGroups, 999, +1))
		assertEquals(2L, ChapterNavigator.target(twoGroups, 2, 0)?.id)
	}

	@Test
	fun multiStepDelta() {
		val list = (1L..6L).map { ch(it, it.toFloat(), null) }
		assertEquals(4L, ChapterNavigator.target(list, 1, +3)?.id)
		assertNull(ChapterNavigator.target(list, 5, +3))
	}
}
