package org.koitharu.kotatsu.search.ui.multi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class SourceTimeoutTest {

	@Test
	fun fastSourceReturnsItsResult() = runBlocking {
		assertEquals("ok", withSourceTimeout(1_000) { "ok" })
	}

	@Test
	fun hungSourceBecomesAnOrdinaryIoError() {
		val error = assertThrows(SocketTimeoutException::class.java) {
			runBlocking { withSourceTimeout(50) { delay(10_000) } }
		}
		assertFalse(error is CancellationException)
	}

	@Test
	fun otherErrorsPassThroughUnchanged() {
		assertThrows(IllegalStateException::class.java) {
			runBlocking { withSourceTimeout(1_000) { error("parser bug") } }
		}
	}

	@Test
	fun cancellingTheSearchStillCancels() = runBlocking {
		val started = CompletableDeferred<Unit>()
		val job = async {
			withSourceTimeout(10_000) { started.complete(Unit); delay(10_000) }
		}
		started.await()
		job.cancel()
		val result = runCatching { job.await() }
		assertTrue(result.exceptionOrNull() is CancellationException)
	}

	@Test
	fun slowSourceDoesNotDelayFastOnesInParallel() = runBlocking {
		val start = System.currentTimeMillis()
		val slow = async { runCatching { withSourceTimeout(300) { delay(10_000) } } }
		val fast = async { withSourceTimeout(300) { "fast" } }
		assertEquals("fast", fast.await())
		assertTrue(System.currentTimeMillis() - start < 250) // fast finished without waiting for slow
		assertTrue(slow.await().exceptionOrNull() is SocketTimeoutException)
	}
}
