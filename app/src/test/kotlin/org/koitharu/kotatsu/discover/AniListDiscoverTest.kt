package org.koitharu.kotatsu.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class AniListDiscoverTest {

	@Test
	fun parsesEnglishTitleFirstAndSkipsUntitled() {
		val body = """
			{"data":{"Page":{"pageInfo":{"hasNextPage":true},"media":[
				{"id":1,"title":{"english":"Given","romaji":"Given"},"coverImage":{"large":"https://img/1.jpg"},"averageScore":82,"isAdult":true},
				{"id":2,"title":{"english":null,"romaji":"Sasaki to Miyano"},"coverImage":{"large":"https://img/2.jpg"},"averageScore":null},
				{"id":3,"title":{"english":null,"romaji":null},"coverImage":null}
			]}}}
		""".trimIndent()
		val page = AniListDiscover.parse(body)
		assertTrue(page.hasNextPage)
		assertEquals(listOf("Given", "Sasaki to Miyano"), page.items.map { it.title })
		assertNull(page.items[0].alternativeTitle)
		assertEquals(82, page.items[0].score)
		assertNull(page.items[1].score)
		assertTrue(page.items[0].isAdult)
		assertFalse(page.items[1].isAdult)
	}

	@Test(expected = IOException::class)
	fun graphQlErrorsAreReported() {
		AniListDiscover.parse("""{"errors":[{"message":"Too Many Requests"}],"data":null}""")
	}

	@Test
	fun boysLoveFeedFiltersByTagAndIncludesAdultTitles() {
		val body = AniListDiscover.buildRequestBody(AniListDiscover.Feed.POPULAR_BL, page = 2)
		val variables = body.getJSONObject("variables")
		assertEquals(2, variables.getInt("page"))
		assertEquals("Boys' Love", variables.getJSONArray("tags").getString(0))
		// No isAdult filter: adult and all-ages titles both come back.
		assertFalse(variables.has("isAdult"))
		assertFalse(body.getString("query").contains("isAdult: false"))
		val adult = AniListDiscover.buildRequestBody(AniListDiscover.Feed.ADULT_BL, 1).getJSONObject("variables")
		assertTrue(adult.getBoolean("isAdult"))
		assertFalse(AniListDiscover.buildRequestBody(AniListDiscover.Feed.TRENDING_MANGA, 1)
			.getJSONObject("variables").has("tags"))
	}
}
