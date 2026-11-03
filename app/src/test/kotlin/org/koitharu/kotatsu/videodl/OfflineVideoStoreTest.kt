package org.koitharu.kotatsu.videodl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineVideoStoreTest {

	private fun entry(id: String = "abc") = OfflineVideoStore.Entry(
		id = id,
		title = "Show - Episode 1",
		sourceName = "JS_demo",
		chapterId = 42L,
		chapterUrl = "https://a.test/ep/1",
		mediaType = "VIDEO",
		streamUrl = "https://cdn.test/v.mp4",
		audioUrl = "https://cdn.test/a.m4a",
		mimeType = "video/mp4",
		protocol = "PROGRESSIVE",
		sizeBytes = 123_456_789L,
		createdAt = 1_700_000_000_000L,
		completed = true,
		cacheUrl = "https://cdn.test/high.m3u8",
		streamKeys = listOf("0:0:1", "0:1:1"),
		headers = mapOf("Referer" to "https://a.test/"),
	)

	@Test
	fun entriesSurviveEncodeAndDecode() {
		val entries = listOf(entry("one"), entry("two").copy(audioUrl = null, completed = false, headers = emptyMap()))
		assertEquals(entries, OfflineVideoStore.decode(OfflineVideoStore.encode(entries)))
	}

	@Test
	fun decodeToleratesMissingFieldsUnknownKeysAndGarbage() {
		assertTrue(OfflineVideoStore.decode(null).isEmpty())
		assertTrue(OfflineVideoStore.decode("").isEmpty())
		assertTrue(OfflineVideoStore.decode("{not json").isEmpty())
		val minimal = """[{"id":"x","title":"T","sourceName":"S","chapterId":1,"chapterUrl":"u","mediaType":"AUDIO","streamUrl":"s","future":true}]"""
		val decoded = OfflineVideoStore.decode(minimal).single()
		assertEquals("x", decoded.id)
		assertEquals(false, decoded.completed)
		assertEquals("PROGRESSIVE", decoded.protocol)
		assertTrue(decoded.headers.isEmpty())
		assertTrue(decoded.streamKeys.isEmpty())
	}

	@Test
	fun entryIdIsStableAndDependsOnBothUrls() {
		val a = OfflineVideoStore.entryId("https://cdn.test/v.mp4", null)
		assertEquals(a, OfflineVideoStore.entryId("https://cdn.test/v.mp4", null))
		assertEquals(24, a.length)
		assertNotEquals(a, OfflineVideoStore.entryId("https://cdn.test/v.mp4", "https://cdn.test/a.m4a"))
		assertNotEquals(a, OfflineVideoStore.entryId("https://cdn.test/other.mp4", null))
	}

	@Test
	fun audioPartIdsMapBackToTheirEntry() {
		val id = OfflineVideoStore.entryId("https://cdn.test/v.mp4", "https://cdn.test/a.m4a")
		val audio = OfflineVideoStore.audioPartId(id)
		assertEquals(id, OfflineVideoStore.entryIdOf(audio))
		assertEquals(id, OfflineVideoStore.entryIdOf(id))
	}
}
