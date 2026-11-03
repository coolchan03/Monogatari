package org.koitharu.kotatsu.storage

import android.content.Context

/**
 * The offline article / episode caches of the news and podcast feeds. They live in the same preference files
 * as the user's subscriptions and saved stories, so only keys with the cache prefix are ever touched.
 */
object FeedCaches {

	/** Same prefix as NewsFeedParser and MediaFeedFragment use for their cached feed contents. */
	const val CACHE_KEY_PREFIX = "cache_v1:"

	private val PREFS_NAMES = listOf("monogatari_news", "monogatari_entertainment_feeds")

	fun isCacheKey(key: String): Boolean = key.startsWith(CACHE_KEY_PREFIX)

	/** Keys of [all] (a SharedPreferences snapshot) that hold cached feed contents. */
	fun cacheKeys(all: Map<String, *>): List<String> = all.keys.filter(::isCacheKey)

	/** Approximate stored size (UTF-8 bytes of the cached values) of the cache entries in [all]. */
	fun sizeOf(all: Map<String, *>): Long = cacheKeys(all).sumOf { key ->
		(all[key] as? String)?.toByteArray(Charsets.UTF_8)?.size?.toLong() ?: 0L
	}

	/** Blocking: call off the main thread. */
	fun measure(context: Context): Long = PREFS_NAMES.sumOf { name ->
		sizeOf(context.getSharedPreferences(name, Context.MODE_PRIVATE).all)
	}

	/** Blocking: call off the main thread. Subscriptions, saved stories and other settings stay as they are. */
	fun clear(context: Context) {
		for (name in PREFS_NAMES) {
			val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
			val keys = cacheKeys(prefs.all)
			if (keys.isEmpty()) continue
			val editor = prefs.edit()
			keys.forEach { editor.remove(it) }
			editor.commit()
		}
	}
}
