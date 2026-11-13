package org.koitharu.kotatsu.search.ui.multi

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LatestSelectionLoaderTest {

	private data class Item(val id: Long)

	@Test fun lateAnswerForReplacedSelectionIsIgnoredAndRequestCancelled() = runTest {
		val gates = mapOf(1L to CompletableDeferred<String>(), 2L to CompletableDeferred<String>())
		val delivered = mutableListOf<Pair<Long, String>>()
		var cancelledFirst = false
		val loader = LatestSelectionLoader<Item, String>(
			scope = TestScope(StandardTestDispatcher(testScheduler)),
			keyOf = { it.id },
			load = { item ->
				try {
					gates.getValue(item.id).await()
				} catch (e: kotlinx.coroutines.CancellationException) {
					if (item.id == 1L) cancelledFirst = true
					throw e
				}
			},
			onResult = { item, result -> delivered += item.id to result.getOrThrow() },
		)
		loader.select(Item(1))
		testScheduler.advanceUntilIdle()
		loader.select(Item(2))
		testScheduler.advanceUntilIdle()
		gates.getValue(1L).complete("old")
		gates.getValue(2L).complete("new")
		testScheduler.advanceUntilIdle()
		assertEquals(listOf(2L to "new"), delivered)
		assertTrue(cancelledFirst)
		assertEquals(2L, loader.selectedKey)
	}

	@Test fun cachedSelectionAnswersImmediatelyWithoutReloading() = runTest {
		var loads = 0
		val delivered = mutableListOf<String>()
		val loader = LatestSelectionLoader<Item, String>(
			scope = TestScope(StandardTestDispatcher(testScheduler)),
			keyOf = { it.id },
			load = { loads++; "v${it.id}" },
			onResult = { _, r -> delivered += r.getOrThrow() },
		)
		loader.select(Item(1)); testScheduler.advanceUntilIdle()
		loader.select(Item(2)); testScheduler.advanceUntilIdle()
		loader.select(Item(1))
		assertEquals(listOf("v1", "v2", "v1"), delivered)
		assertEquals(2, loads)
	}

	@Test fun failuresAreReportedAndNeverCached() = runTest {
		var attempts = 0
		val outcomes = mutableListOf<Boolean>()
		val loader = LatestSelectionLoader<Item, String>(
			scope = TestScope(StandardTestDispatcher(testScheduler)),
			keyOf = { it.id },
			load = { if (attempts++ == 0) error("offline") else "ok" },
			onResult = { _, r -> outcomes += r.isSuccess },
		)
		loader.select(Item(7)); testScheduler.advanceUntilIdle()
		loader.select(Item(7)); testScheduler.advanceUntilIdle()
		assertEquals(listOf(false, true), outcomes)
	}

	@Test fun clearDropsPendingResult() = runTest {
		val gate = CompletableDeferred<String>()
		var delivered = 0
		val loader = LatestSelectionLoader<Item, String>(
			scope = TestScope(StandardTestDispatcher(testScheduler)),
			keyOf = { it.id },
			load = { gate.await() },
			onResult = { _, _ -> delivered++ },
		)
		loader.select(Item(1)); testScheduler.advanceUntilIdle()
		loader.clear()
		gate.complete("late"); testScheduler.advanceUntilIdle()
		assertEquals(0, delivered)
	}
}
