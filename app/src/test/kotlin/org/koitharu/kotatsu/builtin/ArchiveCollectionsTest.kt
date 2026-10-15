package org.koitharu.kotatsu.builtin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveCollectionsTest {

	@Test
	fun friendlyNamesMapToArchiveIds() {
		assertEquals("animationandcartoons", ArchiveCollections.parse("collection:cartoons").collection)
		assertEquals("feature_films", ArchiveCollections.parse("collection:Films").collection)
		assertEquals("opensource_movies", ArchiveCollections.parse("collection:fan").collection)
	}

	@Test
	fun rawIdsPassThroughAndTextRemains() {
		val parsed = ArchiveCollections.parse("night collection:prelinger creature")
		assertEquals("prelinger", parsed.collection)
		assertEquals("night creature", parsed.term)
	}

	@Test
	fun plainSearchHasNoCollection() {
		val parsed = ArchiveCollections.parse("  dracula 1931 ")
		assertNull(parsed.collection)
		assertEquals("dracula 1931", parsed.term)
	}
}
