package org.koitharu.kotatsu.search.ui.multi

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.net.SocketTimeoutException

/** Upper bound for one source's answer in a multi-source search. */
internal const val SOURCE_SEARCH_TIMEOUT_MS = 30_000L

/**
 * Runs [block] with a deadline. A timeout is reported as [SocketTimeoutException] (an ordinary
 * per-source error) rather than a cancellation, so it is shown against that source and never
 * mistaken for the whole search being cancelled; real cancellation still propagates.
 */
internal suspend fun <T> withSourceTimeout(timeoutMs: Long = SOURCE_SEARCH_TIMEOUT_MS, block: suspend () -> T): T =
	try {
		withTimeout(timeoutMs) { block() }
	} catch (e: TimeoutCancellationException) {
		throw SocketTimeoutException("Source did not answer within ${timeoutMs / 1000}s").apply { initCause(e) }
	}
