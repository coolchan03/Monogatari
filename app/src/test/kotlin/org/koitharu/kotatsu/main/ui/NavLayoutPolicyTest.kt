package org.koitharu.kotatsu.main.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavLayoutPolicyTest {

	@Test
	fun desktopWidthExpandsRailOnlyWhenLabelled() {
		assertTrue(NavLayoutPolicy.expandRail(1280, labelsVisible = true))
		assertFalse(NavLayoutPolicy.expandRail(1280, labelsVisible = false))
		assertFalse(NavLayoutPolicy.expandRail(800, labelsVisible = true))
		assertTrue(NavLayoutPolicy.expandRail(1000, labelsVisible = true))
	}

	@Test
	fun twoPaneStartsAt600() {
		assertFalse(NavLayoutPolicy.useTwoPane(599))
		assertTrue(NavLayoutPolicy.useTwoPane(600))
		assertTrue(NavLayoutPolicy.useTwoPane(840))
	}
}
