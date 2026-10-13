package org.koitharu.kotatsu.jsext

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JsVideoExtractorsTest {

	@Test
	fun streamTapeJoinsBothLinkPieces() {
		val page = """<script>document.getElementById('robotlink').innerHTML = '//streamtape.com/get_video?id=abc&expires=1&ip=x&token=' + ('xcdtok123').substring(1).substring(2);</script>"""
		assertEquals("https://streamtape.com/get_video?id=abc&expires=1&ip=x&token=tok123", JsVideoExtractors.parseStreamTape(page))
	}

	@Test
	fun findsM3u8InsidePackedScript() {
		// packed form of: jwplayer().setup({sources:[{file:"https://cdn.example.com/hls/master.m3u8"}]})
		val packed = """eval(function(p,a,c,k,e,d){return p}('0().1({2:[{3:"4://5.6.7/8/9.a"}]})',11,11,'jwplayer|setup|sources|file|https|cdn|example|com|hls|master|m3u8'.split('|'),0,{}))"""
		assertEquals("https://cdn.example.com/hls/master.m3u8", JsVideoExtractors.findM3u8(packed))
		assertNull(JsVideoExtractors.findM3u8("<html>no video here</html>"))
	}

	@Test
	fun voeReadsPlainOrBase64Hls() {
		assertEquals("https://v.example/a.m3u8", JsVideoExtractors.parseVoe("""var s = {'hls': 'https://v.example/a.m3u8'}"""))
		// "https://v.example/b.m3u8" in base64
		assertEquals("https://v.example/b.m3u8", JsVideoExtractors.parseVoe("""var s = {"hls": "aHR0cHM6Ly92LmV4YW1wbGUvYi5tM3U4"}"""))
	}

	@Test
	fun okruParsesDataOptions() {
		val metadata = """{\"videos\":[{\"name\":\"sd\",\"url\":\"https://ok.example/sd.mp4\"},{\"name\":\"hd\",\"url\":\"https://ok.example/hd.mp4\"}],\"hlsManifestUrl\":\"https://ok.example/master.m3u8\"}"""
		val options = """{"flashvars":{"metadata":"$metadata"}}""".replace("\"", "&quot;")
		val videos = JsVideoExtractors.parseOkru("""<div data-options="$options"></div>""")
		assertEquals(listOf("Okru - Auto", "Okru - 720p", "Okru - 480p"), videos.map { it.quality })
		assertEquals("https://ok.example/master.m3u8", videos.first().url)
	}

	@Test
	fun mp4UploadUsesPlayerSource() = runBlocking {
		val transport = JsHttpTransport { request ->
			JsHttpResponse(200, "OK", emptyMap(), """<script>player.src({type: "video/mp4", src: "https://a4.mp4upload.com:183/d/x/video.mp4"}); var HEIGHT = 720;</script>""", request.url)
		}
		val videos = JsVideoExtractors(transport).extract(
			"mp4UploadExtractor",
			JsonArray(listOf(JsonPrimitive("https://www.mp4upload.com/embed-x.html"), JsonPrimitive("{}"), JsonPrimitive("Sub "), JsonPrimitive(""))),
		)!!
		assertEquals("https://a4.mp4upload.com:183/d/x/video.mp4", videos.single().url)
		assertEquals("Sub Mp4Upload - 720p", videos.single().quality)
		assertEquals("https://www.mp4upload.com/", videos.single().headers["Referer"])
	}

	@Test
	fun unknownExtractorIsNullAndBadUrlIsEmpty() = runBlocking {
		val transport = JsHttpTransport { error("no network in tests") }
		val extractors = JsVideoExtractors(transport)
		assertNull(extractors.extract("gogoCdnExtractor", JsonArray(listOf(JsonPrimitive("https://x.example")))))
		assertTrue(extractors.extract("doodExtractor", JsonArray(listOf(JsonPrimitive("not a url"))))!!.isEmpty())
		// Network failures become an empty list instead of crashing the source.
		assertTrue(extractors.extract("doodExtractor", JsonArray(listOf(JsonPrimitive("https://dood.example/e/1"))))!!.isEmpty())
	}
}
