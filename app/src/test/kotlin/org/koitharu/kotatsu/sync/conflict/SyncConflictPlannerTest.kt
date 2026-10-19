package org.koitharu.kotatsu.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.sync.conflict.SyncConflictPlanner.Action
import org.koitharu.kotatsu.sync.conflict.SyncConflictPlanner.Version

class SyncConflictPlannerTest {

	private val lastSync = 1_000L

	@Test
	fun oneSidedChangesFlowWithoutQuestions() {
		val plan = SyncConflictPlanner.plan(
			local = mapOf("a" to Version("ch3", 500), "b" to Version("ch9", 1_500), "c" to Version("x", 10)),
			incoming = mapOf("a" to Version("ch5", 1_200), "b" to Version("ch2", 400), "d" to Version("y", 2_000)),
			lastSyncAt = lastSync,
		).associate { it.key to it.action }
		assertEquals(Action.TAKE_INCOMING, plan["a"])
		assertEquals(Action.SEND_LOCAL, plan["b"])
		assertEquals(Action.SEND_LOCAL, plan["c"])
		assertEquals(Action.TAKE_INCOMING, plan["d"])
	}

	@Test
	fun bothSidesChangedIsAConflictUntilTheUserChooses() {
		val decision = SyncConflictPlanner.decide("a", Version("ch4", 1_100), Version("ch6", 1_300), lastSync)
		assertEquals(Action.CONFLICT, decision.action)
		assertEquals(Action.TAKE_INCOMING, SyncConflictPlanner.resolveNewest(decision))
	}

	@Test
	fun identicalValuesNeedNothingAndDeletionWinsTies() {
		assertEquals(Action.KEEP, SyncConflictPlanner.decide("a", Version("ch4", 5), Version("ch4", 9_999), lastSync).action)
		val tie = SyncConflictPlanner.decide("a", Version("ch4", 2_000), Version(null, 2_000), lastSync)
		assertEquals(Action.CONFLICT, tie.action)
		assertEquals(Action.TAKE_INCOMING, SyncConflictPlanner.resolveNewest(tie))
	}
}
