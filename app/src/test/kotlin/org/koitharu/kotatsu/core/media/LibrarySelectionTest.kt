package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionTest {

	@Test
	fun toggleAddsThenRemovesAKey() {
		val one = LibrarySelection.toggle(emptySet(), "a")
		assertEquals(setOf("a"), one)
		assertEquals(setOf("a", "b"), LibrarySelection.toggle(one, "b"))
		assertEquals(emptySet<String>(), LibrarySelection.toggle(one, "a"))
	}

	@Test
	fun selectAllCoversTheVisibleListAndKeepsOtherSelections() {
		val visible = listOf("a", "b", "c")
		val selected = LibrarySelection.toggleAll(setOf("hidden", "b"), visible)
		assertEquals(setOf("hidden", "a", "b", "c"), selected)
		assertTrue(LibrarySelection.allSelected(selected, visible))
	}

	@Test
	fun selectAllAgainClearsOnlyTheVisibleKeys() {
		val visible = listOf("a", "b")
		val cleared = LibrarySelection.toggleAll(setOf("hidden", "a", "b"), visible)
		assertEquals(setOf("hidden"), cleared)
	}

	@Test
	fun nothingVisibleIsNeverAllSelected() {
		assertFalse(LibrarySelection.allSelected(setOf("a"), emptyList()))
		assertEquals(setOf("a"), LibrarySelection.toggleAll(setOf("a"), emptyList()))
	}

	@Test
	fun pruneDropsKeysThatLeftTheLibrary() {
		assertEquals(setOf("a"), LibrarySelection.prune(setOf("a", "gone"), listOf("a", "b")))
		assertEquals(emptySet<String>(), LibrarySelection.prune(emptySet(), listOf("a")))
	}

	@Test
	fun untouchedCollectionsKeepTheirMembers() {
		val data = mapOf("Mixed" to setOf("a"), "Other" to emptySet())
		val names = LibraryCollections.names(data)
		// Mixed is not ticked (b is not in it), but the user did not touch it.
		val checked = names.map { LibrarySelection.isChecked(data, it, listOf("a", "b")) }
		assertEquals(listOf(false, false), checked)
		val result = LibrarySelection.applyCollections(data, listOf("a", "b"), names, checked, touched = emptySet())
		assertEquals(data, result)
	}

	@Test
	fun touchedCollectionsGetEveryKeyAddedOrRemoved() {
		val data = mapOf("Keep" to setOf("a", "b", "z"), "New" to emptySet())
		val names = listOf("Keep", "New")
		// Keep is ticked (a and b are both in it); the user unticks Keep and ticks New.
		assertTrue(LibrarySelection.isChecked(data, "Keep", listOf("a", "b")))
		val result = LibrarySelection.applyCollections(
			data, listOf("a", "b"), names, checked = listOf(false, true), touched = setOf(0, 1),
		)
		assertEquals(setOf("z"), result["Keep"])
		assertEquals(setOf("a", "b"), result["New"])
	}

	@Test
	fun noKeysMeansNothingIsTicked() {
		assertFalse(LibrarySelection.isChecked(mapOf("A" to setOf("a")), "A", emptyList()))
	}
}
