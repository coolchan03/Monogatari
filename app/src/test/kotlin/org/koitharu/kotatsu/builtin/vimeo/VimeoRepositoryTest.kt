package org.koitharu.kotatsu.builtin.vimeo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.SortOrder

class VimeoRepositoryTest {

	@Test
	fun listUrlEncodesQueryAndSort() {
		val url = VimeoRepository.listUrl("short film", SortOrder.NEWEST, 2)
		assertTrue(url.contains("query=short%20film"))
		assertTrue(url.contains("sort=date"))
		assertTrue(url.contains("page=2"))
	}

	@Test
	fun parsesVideosPicksAThumbnailAndSkipsBadIds() {
		val body = JSONObject("""{"data":[
			{"uri":"/videos/12345","name":"Short","link":"https://vimeo.com/12345","user":{"name":"Studio"},"description":"d",
			 "pictures":{"sizes":[{"width":100,"link":"https://i.vimeocdn.com/a.jpg"},{"width":295,"link":"https://i.vimeocdn.com/b.jpg"},{"width":640,"link":"https://i.vimeocdn.com/c.jpg"}]}},
			{"uri":"/videos/abc","name":"Bad"},
			{"uri":"/videos/777","name":""}]}""")
		val videos = VimeoRepository.parseVideos(body)
		assertEquals(1, videos.size)
		assertEquals("https://i.vimeocdn.com/b.jpg", videos[0].thumbnail)
		assertEquals("Studio", videos[0].owner)
		assertEquals("https://vimeo.com/12345", videos[0].link)
	}
}
