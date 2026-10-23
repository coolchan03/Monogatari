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
		// Reminders follow the show, not the episode card.
		assertEquals(listOf(7, 7), page.items.map { it.aniListMediaId })
	}

	@Test
	fun airingRequestCoversTheRequestedWindow() {
		val variables = AniListDiscover.buildAiringRequestBody(page = 1, from = 100L, to = 700L).getJSONObject("variables")
		assertEquals(100, variables.getInt("from"))
		assertEquals(700, variables.getInt("to"))
	}

	@Test
	fun followedAiringRequestFiltersByMediaIdsAndWindow() {
		val body = AniListDiscover.buildFollowedAiringRequestBody(listOf(42, 7, 42), from = 100L, to = 700L)
		val variables = body.getJSONObject("variables")
		val ids = variables.getJSONArray("ids")
		assertEquals(listOf(7, 42), (0 until ids.length()).map { ids.getInt(it) })
		assertEquals(100, variables.getInt("from"))
		assertEquals(700, variables.getInt("to"))
		assertEquals(1, variables.getInt("page"))
		val query = body.getString("query")
		assertTrue(query.contains("mediaId_in"))
		assertTrue(query.contains("sort: TIME"))
	}

	@Test
	fun parsesFollowedAiringEpisodes() {
		val body = """
			{"data":{"Page":{"pageInfo":{"hasNextPage":true},"airingSchedules":[
				{"episode":5,"airingAt":1000,"media":{"id":7,"title":{"english":null,"romaji":"Sasaki to Miyano"}}},
				{"episode":6,"airingAt":2000,"media":{"id":8,"title":{"english":"Given","romaji":"Given"}}},
				{"episode":1,"airingAt":3000,"media":null},
				{"episode":2,"airingAt":4000,"media":{"title":{"english":"No id"}}}
			]}}}
		""".trimIndent()
		val page = AniListDiscover.parseFollowedAiring(body)
		assertTrue(page.hasNextPage)
		assertEquals(
			listOf(
				AniListDiscover.AiredEpisode(7, "Sasaki to Miyano", 5, 1000L),
				AniListDiscover.AiredEpisode(8, "Given", 6, 2000L),
			),
			page.episodes,
		)
	}

	@Test(expected = IOException::class)
	fun followedAiringErrorsAreReported() {
		AniListDiscover.parseFollowedAiring("""{"errors":[{"message":"Too Many Requests"}],"data":null}""")
	}

	@Test
	fun strongNonSpoilerTagsJoinGenresForTaste() {
		val page = AniListDiscover.parse("""{"data":{"Page":{"pageInfo":{"hasNextPage":false},"media":[
			{"id":7,"title":{"english":"Given"},"genres":["Drama","Romance"],"tags":[
				{"name":"Boys' Love","rank":95,"isMediaSpoiler":false},
				{"name":"Music","rank":80,"isMediaSpoiler":false},
				{"name":"Tragedy","rank":90,"isMediaSpoiler":true},
				{"name":"Kissing","rank":20,"isMediaSpoiler":false},
				{"name":"Romance","rank":70,"isMediaSpoiler":false}]}]}}}""")
		assertEquals(listOf("Drama", "Romance", "Boys' Love", "Music"), page.items.single().genres)
		assertTrue(AniListDiscover.buildRequestBody(AniListDiscover.Feed.TRENDING_BL, 1).getString("query").contains("tags {"))
	}

	@Test
	fun httpErrorBodiesKeepAniListsMessage() {
		assertEquals("Invalid tag", AniListDiscover.errorMessage("""{"errors":[{"message":"Invalid tag","status":400}],"data":null}"""))
		assertNull(AniListDiscover.errorMessage("<html>Just a moment...</html>"))
		assertEquals("Unknown error", AniListDiscover.describe(IOException("Unknown error")))
		assertEquals("IOException", AniListDiscover.describe(IOException()))
	}

	@Test
	fun airingCardsOfDifferentShowsNeverShareAUniqueKey() {
		// 100 * 31 + 32 == 101 * 31 + 1, so the numeric id alone cannot tell these episodes apart.
		assertEquals(100 * 31 + 32, 101 * 31 + 1)
		assertTrue(AniListDiscover.airingKey(100, 32) != AniListDiscover.airingKey(101, 1))
		val body = """
			{"data":{"Page":{"pageInfo":{"hasNextPage":false},"airingSchedules":[
				{"episode":32,"airingAt":1000,"media":{"id":100,"title":{"english":"A"}}},
				{"episode":1,"airingAt":2000,"media":{"id":101,"title":{"english":"B"}}}
			]}}}
		""".trimIndent()
		val items = AniListDiscover.parseAiring(body) { episode, _ -> "Ep $episode" }.items
		assertEquals(1, items.map { it.id }.distinct().size)
		assertEquals(2, items.map { it.uniqueKey }.distinct().size)
	}

	@Test
	fun nonAiringItemsKeepTheirIdAsUniqueKey() {
		val item = AniListDiscover.Item(id = 5, title = "T", alternativeTitle = null, coverUrl = null, score = null)
		assertEquals("5", item.uniqueKey)
	}

	@Test
	fun cacheKeysDifferPerProviderAndPage() {
		val feed = AniListDiscover.Feed.TRENDING_MANGA
		val aniList = AniListDiscover.Provider.ANILIST
		val jikan = AniListDiscover.Provider.JIKAN
		assertTrue(AniListDiscover.cacheKey(feed, aniList, 1) != AniListDiscover.cacheKey(feed, jikan, 1))
		assertTrue(AniListDiscover.cacheKey(feed, jikan, 1) != AniListDiscover.cacheKey(feed, jikan, 2))
		assertTrue(AniListDiscover.cacheKey(feed, aniList, 2) != AniListDiscover.cacheKey(AniListDiscover.Feed.TRENDING_BL, aniList, 2))
	}

	@Test
	fun adultTitlesAreHiddenOnlyWhenTheSettingIsOn() {
		val safe = AniListDiscover.Item(id = 1, title = "A", alternativeTitle = null, coverUrl = null, score = null)
		val adult = safe.copy(id = 2, title = "B", isAdult = true)
		assertEquals(listOf(safe, adult), AniListDiscover.visibleItems(listOf(safe, adult), hideAdult = false))
		assertEquals(listOf(safe), AniListDiscover.visibleItems(listOf(safe, adult), hideAdult = true))
	}
}
