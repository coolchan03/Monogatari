package org.koitharu.kotatsu.reader.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrefetchTrackerTest {

	@Test
	fun pagesOutsideTheNewWindowBecomeObsolete() {
		val t = PrefetchTracker()
		listOf(1L, 2L, 3L).forEach(t::markPrefetching)
		assertEquals(listOf(1L), t.takeObsolete(listOf(2L, 3L, 4L)))
		// Already reported pages are forgotten, not reported twice.
		assertTrue(t.takeObsolete(listOf(2L, 3L, 4L)).isEmpty())
		assertEquals(2, t.size())
	}

	@Test
	fun demandedPageIsNeverCancelledByWindowChange() {
		val t = PrefetchTracker()
		t.markPrefetching(10)
		t.markDemanded(10)
		assertTrue(t.takeObsolete(emptyList()).isEmpty())
	}

	@Test
	fun finishedPagesAreNotTracked() {
		val t = PrefetchTracker()
		t.markPrefetching(5)
		t.markFinished(5)
		assertTrue(t.takeObsolete(emptyList()).isEmpty())
		assertEquals(0, t.size())
	}

	@Test
	fun jumpingToAnotherChapterObsoletesEverything() {
		val t = PrefetchTracker()
		(1L..5L).forEach(t::markPrefetching)
		assertEquals((1L..5L).toList(), t.takeObsolete(listOf(100L, 101L)).sorted())
	}

	@Test
	fun concurrentUseKeepsStateConsistent() {
		val t = PrefetchTracker()
		val threads = (0 until 8).map { n ->
			Thread { repeat(500) { i -> val id = (n * 1000 + i).toLong(); t.markPrefetching(id); if (i % 2 == 0) t.markFinished(id) } }
		}
		threads.forEach(Thread::start); threads.forEach(Thread::join)
		assertEquals(8 * 250, t.size())
		assertEquals(8 * 250, t.takeObsolete(emptyList()).size)
	}
}
