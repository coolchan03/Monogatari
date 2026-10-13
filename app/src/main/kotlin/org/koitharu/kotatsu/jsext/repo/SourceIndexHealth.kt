package org.koitharu.kotatsu.jsext.repo

import java.time.Instant

/**
 * Only suppress an index entry with a recent, explicit "broken" verdict.
 * Expired reports, unknown values and absent dates remain visible for manual retry.
 */
internal object SourceIndexHealth {
    private const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    private const val CLOCK_SKEW_MS = 5L * 60 * 1000

    fun shouldHide(status: String?, checkedAt: String?, now: Long = System.currentTimeMillis()): Boolean {
        if (!status.equals("broken", ignoreCase = true) || checkedAt.isNullOrBlank()) return false
        val whenChecked = runCatching { Instant.parse(checkedAt).toEpochMilli() }.getOrNull()
            ?: return false
        return whenChecked >= now - MAX_AGE_MS && whenChecked <= now + CLOCK_SKEW_MS
    }
}
