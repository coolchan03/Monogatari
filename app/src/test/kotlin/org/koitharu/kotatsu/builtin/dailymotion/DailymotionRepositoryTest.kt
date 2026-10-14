package org.koitharu.kotatsu.builtin.dailymotion

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.IOException

class DailymotionRepositoryTest {

	@Test
	fun listUrlUsesTrendingWithoutQueryAndEncodesSearch() {
		val trending = DailymotionRepository.listUrl("", SortOrder.POPULARITY, page = 1)
		assertTrue(trending.contains("sort=trending"))
		assertTrue(!trending.contains("search="))
		val search = DailymotionRepository.listUrl("given anime", SortOrder.RELEVANCE, page = 2)
		assertTrue(search.contains("search=given%20anime"))
		assertTrue(search.contains("sort=relevance"))
		assertTrue(search.contains("page=2"))
	}

	@Test
	fun parsesVideosAndSkipsPrivateOrInvalidOnes() {
		val body = JSONObject(
			"""{"page":1,"has_more":true,"list":[
				{"id":"x8abcde","title":"Trailer","thumbnail_480_url":"https://s1.dmcdn.net/t.jpg","owner.screenname":"Studio","channel.name":"Anime","explicit":false},
				{"id":"x8hidden","title":"Hidden","private":true},
				{"id":"bad id!","title":"Broken"},
				{"id":"x8zzzzz","title":"","explicit":true}
			]}""",
		)
		val videos = DailymotionRepository.parseList(body)
		assertEquals(listOf("x8abcde"), videos.map { it.id })
		assertEquals("Studio", videos.single().owner)
		assertEquals("Anime", videos.single().channel)
	}

	@Test
	fun streamsPutAdaptiveFirstThenHighestQuality() {
		val metadata = JSONObject(
			"""{"qualities":{
				"380":[{"type":"application/x-mpegURL","url":"https://cdn/380.m3u8"}],
				"auto":[{"type":"application/x-mpegURL","url":"https://cdn/master.m3u8"}],
				"1080":[{"type":"application/x-mpegURL","url":"https://cdn/1080.m3u8"}]
			}}""",
		)
		val streams = DailymotionRepository.parseStreams(metadata)
		assertEquals(listOf("Auto", "1080p", "380p"), streams.map { it.quality })
		assertEquals("https://cdn/master.m3u8", streams.first().url)
	}

	@Test(expected = IOException::class)
	fun restrictedVideoReportsReason() {
		DailymotionRepository.parseStreams(JSONObject("""{"error":{"title":"This video is not available in your country"}}"""))
	}
}
