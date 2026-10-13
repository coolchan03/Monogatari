package org.koitharu.kotatsu.jsext

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** Bounded process-local counters; never collect extension arguments, credentials or URLs. */
internal object MissingJsHostDiagnostics {
    private const val MAX_NAMES = 100
    private val counts = ConcurrentHashMap<String, AtomicInteger>()

    fun record(name: String) {
        if (!name.matches(Regex("[A-Za-z][A-Za-z0-9_:]{0,79}"))) return
        if (!counts.containsKey(name) && counts.size >= MAX_NAMES) return
        counts.computeIfAbsent(name) { AtomicInteger() }.updateAndGet { old ->
            if (old == Int.MAX_VALUE) old else old + 1
        }
    }

    fun snapshot(): Map<String, Int> = counts.mapValues { it.value.get() }.toSortedMap()

    fun clear() = counts.clear()
}
