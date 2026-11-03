package org.koitharu.kotatsu.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class StorageBreakdownTest {

	private val mb = 1024L * 1024L
	private val units = listOf("B", "kB", "MB", "GB", "TB")

	private fun entry(id: String, bytes: Long, action: StorageAction) = StorageEntry(id, bytes, action)

	@Test
	fun formatBytes() {
		assertEquals("0 B", StorageBreakdown.formatBytes(0, units, Locale.US))
		assertEquals("0 B", StorageBreakdown.formatBytes(-5, units, Locale.US))
		assertEquals("512 B", StorageBreakdown.formatBytes(512, units, Locale.US))
		assertEquals("1 kB", StorageBreakdown.formatBytes(1024, units, Locale.US))
		assertEquals("1.5 MB", StorageBreakdown.formatBytes(mb + mb / 2, units, Locale.US))
		assertEquals("2 GB", StorageBreakdown.formatBytes(2048 * mb, units, Locale.US))
		assertEquals("2,048 TB", StorageBreakdown.formatBytes(2048L * 1024 * 1024 * 1024 * 1024, units, Locale.US))
	}

	@Test
	fun lowStorageThreshold() {
		assertTrue(StorageBreakdown.isLowStorage(0))
		assertTrue(StorageBreakdown.isLowStorage(500 * mb - 1))
		assertFalse(StorageBreakdown.isLowStorage(500 * mb))
		assertFalse(StorageBreakdown.isLowStorage(-1)) // unknown
		assertTrue(StorageBreakdown.isLowStorage(900 * mb, threshold = 1024 * mb))
	}

	@Test
	fun shares() {
		assertEquals(0f, StorageBreakdown.shareFraction(10, 0), 0f)
		assertEquals(0f, StorageBreakdown.shareFraction(-10, 100), 0f)
		assertEquals(0.25f, StorageBreakdown.shareFraction(25, 100), 0.0001f)
		assertEquals(1f, StorageBreakdown.shareFraction(500, 100), 0f)
		assertEquals(33, StorageBreakdown.sharePercent(1, 3))
		assertEquals(67, StorageBreakdown.sharePercent(2, 3))
		assertEquals(0, StorageBreakdown.sharePercent(0, 3))
	}

	@Test
	fun excludingNeverNegative() {
		assertEquals(70L, StorageBreakdown.excluding(100, 30))
		assertEquals(0L, StorageBreakdown.excluding(10, 30))
	}

	@Test
	fun biggestClearable() {
		val entries = listOf(
			entry("books", 900 * mb, StorageAction.OPEN_LIBRARY),
			entry("saved", 800 * mb, StorageAction.MANAGE),
			entry("code", 700 * mb, StorageAction.NONE),
			entry("pages", 50 * mb, StorageAction.CLEAR),
			entry("auto", 120 * mb, StorageAction.REMOVE_AUTO_DOWNLOADS),
			entry("feeds", 1 * mb, StorageAction.CLEAR),
		)
		assertEquals("auto", StorageBreakdown.biggestClearable(entries)?.id)
		assertEquals(
			"pages",
			StorageBreakdown.biggestClearable(entries.filterNot { it.id == "auto" })?.id,
		)
	}

	@Test
	fun biggestClearableIgnoresEmptyAndMissing() {
		assertNull(StorageBreakdown.biggestClearable(emptyList()))
		assertNull(StorageBreakdown.biggestClearable(listOf(entry("pages", 0, StorageAction.CLEAR))))
		assertNull(StorageBreakdown.biggestClearable(listOf(entry("books", 5 * mb, StorageAction.OPEN_LIBRARY))))
	}

	@Test
	fun overview() {
		val overview = StorageOverview(
			entries = listOf(
				entry("a", 10, StorageAction.CLEAR),
				entry("b", -4, StorageAction.NONE),
				entry("c", 5, StorageAction.NONE),
			),
			freeBytes = 100 * mb,
			totalBytes = 10_000 * mb,
		)
		assertEquals(15L, overview.appBytes)
		assertTrue(overview.isLowStorage)
		assertEquals("a", overview.biggestClearable?.id)
	}

	@Test
	fun extensionPointIsEmptyByDefault() {
		assertTrue(StorageEntryProviders.extra.isEmpty())
	}

	@Test
	fun feedCacheKeysOnly() {
		val all = mapOf<String, Any?>(
			"cache_v1:abc" to "12345",
			"cache_v1:def" to "é", // 2 bytes in UTF-8
			"feeds" to setOf("https://example.org/rss"),
			"saved_stories" to setOf("x"),
			"selected" to "all",
			"urls" to setOf("u"),
			"playlist_episodes" to "keep",
		)
		assertEquals(listOf("cache_v1:abc", "cache_v1:def"), FeedCaches.cacheKeys(all))
		assertEquals(7L, FeedCaches.sizeOf(all))
		assertEquals(0L, FeedCaches.sizeOf(emptyMap<String, Any?>()))
		assertTrue(FeedCaches.isCacheKey("cache_v1:"))
		assertFalse(FeedCaches.isCacheKey("saved_stories"))
	}
}
