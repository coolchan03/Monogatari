package org.koitharu.kotatsu.builtin.odysee

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.IOException

class OdyseeRepositoryTest {

	private val id = "a".repeat(40)

	@Test
	fun searchRequestFiltersToFreeNonMatureVideos() {
		val params = OdyseeRepository.searchRequest("anime", SortOrder.RELEVANCE, page = 2).getJSONObject("params")
		assertEquals("anime", params.getString("text"))
		assertEquals(2, params.getInt("page"))
		assertEquals("<=0", params.getString("fee_amount"))
		assertTrue(params.getJSONArray("not_tags").toString().contains("nsfw"))
		assertTrue(!params.has("order_by"))
		val trending = OdyseeRepository.searchRequest("", SortOrder.POPULARITY, 1).getJSONObject("params")
		assertEquals("trending_group", trending.getJSONArray("order_by").getString(0))
		assertTrue(!trending.has("text"))
	}

	@Test
	fun parsesClaimsAndSkipsMatureOrInvalid() {
		val body = JSONObject(
			"""{"result":{"items":[
				{"claim_id":"$id","name":"my-video","value":{"title":"My Video","description":"d","tags":["anime","fun"],
					"thumbnail":{"url":"https://thumbs.odycdn.com/t.jpg"},"source":{"sd_hash":"abcdef0123456789"}},
					"signing_channel":{"name":"@chan","value":{"title":"Chan"}}},
				{"claim_id":"$id","name":"adult","value":{"title":"X","tags":["Porn"],"source":{"sd_hash":"abcdef"}}},
				{"claim_id":"short","name":"bad","value":{"title":"Y","source":{}}},
				{"claim_id":"$id","name":"no-source","value":{"title":"Z"}}
			]}}""",
		)
		val claims = OdyseeRepository.parseClaims(body)
		assertEquals(1, claims.size)
		assertEquals("Chan", claims[0].channel)
		assertEquals(listOf("anime", "fun"), claims[0].tags)
		assertEquals("https://thumbs.odycdn.com/t.jpg", claims[0].thumbnail)
	}

	@Test
	fun resolveResultIsKeyedByUri() {
		val body = JSONObject("""{"result":{"lbry://x#$id":{"claim_id":"$id","name":"x","value":{"title":"T"}}}}""")
		assertEquals("T", OdyseeRepository.parseClaims(body, requireSource = false).single().title)
	}

	@Test(expected = IOException::class)
	fun rpcErrorsSurface() {
		OdyseeRepository.parseClaims(JSONObject("""{"error":{"message":"Backend unavailable"}}"""))
	}

	@Test
	fun streamsPreferCdnWithRedirectFallback() {
		val claim = OdyseeRepository.Claim("n", id, "T", null, null, null, emptyList(), "abcdef0123")
		val streams = OdyseeRepository.streamsFor(claim)
		assertEquals("https://player.odycdn.com/api/v3/streams/free/n/$id/abcdef.mp4", streams[0].url)
		assertEquals("https://odysee.com/\$/download/n/$id", streams[1].url)
		assertEquals(1, OdyseeRepository.streamsFor(claim.copy(sdHash = null)).size)
	}

	@Test
	fun splitsAndValidatesStoredUrl() {
		assertEquals("n" to id, OdyseeRepository.splitUrl("/n/$id"))
		assertNull(OdyseeRepository.splitUrl("/n/zzz"))
		assertNull(OdyseeRepository.splitUrl("/a/b/c"))
	}
}
