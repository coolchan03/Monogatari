package org.koitharu.kotatsu.main.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.main.ui.welcome.SetupFlow.Step

class SetupFlowTest {

	@Test
	fun freshInstallShowsSetupAfterWelcome() {
		assertTrue(SetupFlow.shouldShow(setupDone = false, welcomeJustCompleted = true))
	}

	@Test
	fun finishedOrSkippedSetupNeverReturnsOnItsOwn() {
		assertFalse(SetupFlow.shouldShow(setupDone = true, welcomeJustCompleted = true))
	}

	@Test
	fun existingUsersWhoDidNotJustSeeTheWelcomeAreLeftAlone() {
		assertFalse(SetupFlow.shouldShow(setupDone = false, welcomeJustCompleted = false))
	}

	@Test
	fun notificationsStepIsSkippedBeforeAndroid13() {
		assertEquals(listOf(Step.THEME, Step.SOURCES, Step.WHERE), SetupFlow.steps(32))
		assertEquals(Step.entries.toList(), SetupFlow.steps(33))
	}

	@Test
	fun stepIndexStaysInRange() {
		assertEquals(0, SetupFlow.previous(0))
		assertEquals(1, SetupFlow.next(0, 4))
		assertEquals(3, SetupFlow.next(3, 4))
	}
}
