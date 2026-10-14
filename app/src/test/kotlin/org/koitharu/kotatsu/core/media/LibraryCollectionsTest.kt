package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCollectionsTest {

	@Test
	fun addRemoveAndLookup() {
		var data = emptyMap<String, Set<String>>()
		data = LibraryCollections.add(data, "  Weekend   reads ", "42")
		data = LibraryCollections.add(data, "Weekend reads", "/books/a.epub")
		data = LibraryCollections.add(data, "Favourites", "42")
		assertEquals(setOf("42", "/books/a.epub"), data.getValue("Weekend reads"))
		assertEquals(listOf("Favourites", "Weekend reads"), LibraryCollections.containing(data, "42"))
		data = LibraryCollections.remove(data, "Favourites", "42")
		assertEquals(listOf("Weekend reads"), LibraryCollections.containing(data, "42"))
		data = LibraryCollections.delete(data, "Weekend reads")
		assertEquals(listOf("Favourites"), LibraryCollections.names(data))
	}

	@Test
	fun blankInputIsIgnoredAndNamesAreBounded() {
		val data = mapOf("A" to setOf("1"))
		assertEquals(data, LibraryCollections.add(data, "   ", "2"))
		assertEquals(data, LibraryCollections.add(data, "B", " "))
		assertEquals(LibraryCollections.MAX_NAME, LibraryCollections.clean("x".repeat(100)).length)
	}

	@Test
	fun collectionCountIsCapped() {
		var data = emptyMap<String, Set<String>>()
		repeat(LibraryCollections.MAX_COLLECTIONS + 5) { data = LibraryCollections.add(data, "c$it", "k") }
		assertEquals(LibraryCollections.MAX_COLLECTIONS, data.size)
	}

	@Test
	fun roundTripsThroughJsonAndSurvivesGarbage() {
		val data = mapOf("Mix" to setOf("1", "/x.pdf"), "Empty" to emptySet())
		assertEquals(data, LibraryCollections.decode(LibraryCollections.encode(data)))
		assertEquals(emptyMap<String, Set<String>>(), LibraryCollections.decode("not json"))
		assertEquals(emptyMap<String, Set<String>>(), LibraryCollections.decode(null))
	}
}
