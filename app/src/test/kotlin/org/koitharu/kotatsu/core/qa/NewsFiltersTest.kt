package org.koitharu.kotatsu.core.qa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFiltersTest {

	@Test
	fun hostDropsWwwAndRejectsNonWebText() {
		assertEquals("animenewsnetwork.com", NewsFilters.host("https://www.animenewsnetwork.com/news/1"))
		assertNull(NewsFilters.host("not a link"))
	}

	@Test
	fun sourcesAreUniqueAndSorted() {
		val links = listOf("https://b.com/1", "https://a.com/2", "https://www.b.com/3", "junk")
		assertEquals(listOf("a.com", "b.com"), NewsFilters.sources(links))
	}

	@Test
	fun sourceCyclesThroughAllAndBack() {
		val all = listOf("a.com", "b.com")
		assertEquals("a.com", NewsFilters.nextSource(null, all))
		assertEquals("b.com", NewsFilters.nextSource("a.com", all))
		assertNull(NewsFilters.nextSource("b.com", all))
		assertNull(NewsFilters.nextSource("gone.com", all))
		assertNull(NewsFilters.nextSource(null, emptyList()))
	}

	@Test
	fun tagAndSourceMatching() {
		assertTrue(NewsFilters.matchesTag(listOf("Anime", "Film"), "anime"))
		assertFalse(NewsFilters.matchesTag(listOf("Film"), "anime"))
		assertTrue(NewsFilters.matchesTag(emptyList(), null))
		assertTrue(NewsFilters.matchesSource("https://www.a.com/x", "a.com"))
		assertFalse(NewsFilters.matchesSource("https://b.com/x", "a.com"))
	}
}
