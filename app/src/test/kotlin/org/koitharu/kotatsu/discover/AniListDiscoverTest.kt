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

	@Test
	fun parsesGenresForTheForYouRanking() {
		val page = AniListDiscover.parse("""{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[
			{"id":7,"title":{"english":"Frieren"},"genres":["Adventure","Fantasy",""]}]}}}""")
		org.junit.Assert.assertEquals(listOf("Adventure", "Fantasy"), page.items.single().genres)
		org.junit.Assert.assertTrue(AniListDiscover.buildRequestBody(AniListDiscover.Feed.entries.first(), 1)
			.getString("query").contains("genres"))
	}

	@Test
	fun airingScheduleGivesOneCardPerEpisodeWithLabel() {
		val body = """
			{"data":{"Page":{"pageInfo":{"hasNextPage":false},"airingSchedules":[
				{"episode":5,"airingAt":1000,"media":{"id":7,"title":{"english":"Given","romaji":"Given"},"coverImage":{"large":"https://img/7.jpg"},"isAdult":false}},
				{"episode":6,"airingAt":2000,"media":{"id":7,"title":{"english":"Given","romaji":"Given"},"coverImage":{"large":"https://img/7.jpg"}}},
				{"episode":1,"airingAt":3000,"media":null}
			]}}}
		""".trimIndent()
		val page = AniListDiscover.parseAiring(body) { episode, at -> "Ep $episode @$at" }
		assertFalse(page.hasNextPage)
		assertEquals(listOf("Ep 5 @1000", "Ep 6 @2000"), page.items.map { it.providerLabel })
		assertEquals(2, page.items.map { it.id }.distinct().size)
	}

	@Test
	fun airingRequestCoversTheRequestedWindow() {
		val variables = AniListDiscover.buildAiringRequestBody(page = 1, from = 100L, to = 700L).getJSONObject("variables")
		assertEquals(100, variables.getInt("from"))
		assertEquals(700, variables.getInt("to"))
	}
}
