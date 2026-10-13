package org.koitharu.kotatsu.sourcehealth

/** A manual visit always remains possible: these results only affect automatic global sweeps. */
object SourceHealthPolicy {
    const val FAIL_THRESHOLD = 5
    const val OLD_SUCCESS_MS = 7L * 24 * 60 * 60 * 1000
    fun isUnhealthy(failures: Int, lastSuccessAt: Long, now: Long): Boolean =
        failures >= FAIL_THRESHOLD && lastSuccessAt < now - OLD_SUCCESS_MS
    fun shouldRollback(failures: Int, lastSuccessAt: Long, now: Long, freshUpdate: Boolean): Boolean =
        freshUpdate && failures >= 3 && lastSuccessAt > 0 &&
            lastSuccessAt >= now - OLD_SUCCESS_MS
}
