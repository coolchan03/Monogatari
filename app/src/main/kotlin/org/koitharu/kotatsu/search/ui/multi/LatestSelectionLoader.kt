package org.koitharu.kotatsu.search.ui.multi

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Loads details for the most recently selected item only. Selecting another item cancels the running request
 * and a late answer for an older selection is ignored, so a fast list scrub never shows stale details.
 * Successful results can be cached (bounded, least recently used) to make re-selection instant.
 */
class LatestSelectionLoader<T : Any, V : Any>(
	private val scope: CoroutineScope,
	private val keyOf: (T) -> Long,
	private val load: suspend (T) -> V,
	private val cacheable: (V) -> Boolean = { true },
	private val onResult: (T, Result<V>) -> Unit,
	private val cacheSize: Int = 16,
) {
	private var job: Job? = null
	private var currentKey: Long? = null
	private val cache = object : LinkedHashMap<Long, V>(16, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, V>?) = size > cacheSize
	}

	val selectedKey: Long? get() = currentKey

	fun select(item: T) {
		val key = keyOf(item)
		job?.cancel()
		currentKey = key
		cache[key]?.let { cached ->
			job = null
			onResult(item, Result.success(cached))
			return
		}
		job = scope.launch {
			val result = try {
				Result.success(load(item))
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				Result.failure(e)
			}
			if (currentKey != key) return@launch
			result.getOrNull()?.takeIf(cacheable)?.let { cache[key] = it }
			onResult(item, result)
		}
	}

	fun clear() {
		job?.cancel()
		job = null
		currentKey = null
	}

	/** Forgets a cached answer, e.g. when the user retries a failed or outdated load. */
	fun invalidate(item: T) {
		cache.remove(keyOf(item))
	}
}
