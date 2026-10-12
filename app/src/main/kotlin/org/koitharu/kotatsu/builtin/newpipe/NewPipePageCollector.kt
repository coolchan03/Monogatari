package org.koitharu.kotatsu.builtin.newpipe

import kotlinx.coroutines.CancellationException

/**
 * Bounded pagination for a channel or playlist detail screen.
 * Deduplicates items by stable video URL and stops on empty or repeated pages.
 */
internal object NewPipePageCollector {
    const val MAX_PAGES = 8
    const val MAX_ITEMS = 240

    suspend fun <T, P> collect(
        first: List<T>,
        next: P?,
        key: (T) -> String,
        fetch: suspend (P) -> Pair<List<T>, P?>,
    ): List<T> {
        val unique = LinkedHashMap<String, T>()
        first.forEach { unique.putIfAbsent(key(it), it) }
        var token = next
        var pageCount = 1
        while (token != null && pageCount < MAX_PAGES && unique.size < MAX_ITEMS) {
            val (batch, following) = try {
                fetch(token)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Keep the already retrieved chapters when a continuation page expires or fails.
                break
            }
            if (batch.isEmpty()) break
            var added = 0
            batch.forEach { item ->
                if (unique.size < MAX_ITEMS && unique.putIfAbsent(key(item), item) == null) added++
            }
            if (added == 0) break
            token = following
            pageCount++
        }
        return unique.values.take(MAX_ITEMS)
    }
}
