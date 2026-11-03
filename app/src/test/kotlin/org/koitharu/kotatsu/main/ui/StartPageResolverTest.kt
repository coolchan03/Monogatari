package org.koitharu.kotatsu.main.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.prefs.NavItem

class StartPageResolverTest {

	private val bar = listOf(NavItem.UNIFIED_LIBRARY, NavItem.UPDATED, NavItem.HISTORY, NavItem.DISCOVER, NavItem.MORE)
	private val all: (NavItem) -> Boolean = { true }

	@Test
	fun firstAndNullUseFirstTab() {
		assertEquals(NavItem.UNIFIED_LIBRARY, StartPageResolver.resolve("FIRST", null, bar, all))
		assertEquals(NavItem.UNIFIED_LIBRARY, StartPageResolver.resolve(null, "HISTORY", bar, all))
	}

	@Test
	fun firstSkipsLeadingShortcutsAndUnavailable() {
		val items = listOf(NavItem.SEARCH, NavItem.UPDATED, NavItem.HISTORY)
		assertEquals(NavItem.HISTORY, StartPageResolver.resolve("FIRST", null, items) { it != NavItem.UPDATED })
	}

	@Test
	fun lastTabIsUsedWhenOnBar() {
		assertEquals(NavItem.HISTORY, StartPageResolver.resolve("LAST", "HISTORY", bar, all))
	}

	@Test
	fun lastTabRemovedFromBarFallsBackToFirst() {
		assertEquals(NavItem.UNIFIED_LIBRARY, StartPageResolver.resolve("LAST", "FEED", bar, all))
	}

	@Test
	fun lastTabMissingOrUnknownFallsBackToFirst() {
		assertEquals(NavItem.UNIFIED_LIBRARY, StartPageResolver.resolve("LAST", null, bar, all))
		assertEquals(NavItem.UNIFIED_LIBRARY, StartPageResolver.resolve("LAST", "nope", bar, all))
	}

	@Test
	fun lastTabShortcutIsIgnored() {
		assertEquals(NavItem.UNIFIED_LIBRARY, StartPageResolver.resolve("LAST", "MORE", bar, all))
	}

	@Test
	fun specificTabIsUsedWhenAvailable() {
		assertEquals(NavItem.DISCOVER, StartPageResolver.resolve("DISCOVER", null, bar, all))
	}

	@Test
	fun specificTabNotOnBarOrUnavailableFallsBackToFirst() {
		assertEquals(NavItem.UNIFIED_LIBRARY, StartPageResolver.resolve("FAVORITES", null, bar, all))
		assertEquals(
			NavItem.UNIFIED_LIBRARY,
			StartPageResolver.resolve("UPDATED", null, bar) { it != NavItem.UPDATED },
		)
	}

	@Test
	fun noTabsGivesNull() {
		assertNull(StartPageResolver.resolve("LAST", "HISTORY", listOf(NavItem.MORE, NavItem.SETTINGS), all))
	}

	@Test
	fun tabsOfExcludesShortcuts() {
		assertTrue(StartPageResolver.tabsOf(bar).none { it.isShortcut })
		assertEquals(4, StartPageResolver.tabsOf(bar).size)
	}
}
