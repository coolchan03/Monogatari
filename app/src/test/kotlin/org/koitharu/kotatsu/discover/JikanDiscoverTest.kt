package org.koitharu.kotatsu.discover

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class JikanDiscoverTest {

	@Test
	fun parsesJikanV4MangaListIntoDiscoverItems() {
		val body = """
			{"pagination":{"last_visible_page":40,"has_next_page":true,"current_page":1},
			"data":[
				{"mal_id":2,"url":"https://myanimelist.net/manga/2/Berserk","title":"Berserk","title_english":"Berserk",
					"images":{"jpg":{"image_url":"https://cdn.myanimelist.net/images/manga/1/157897.jpg",
						"large_image_url":"https://cdn.myanimelist.net/images/manga/1/157897l.jpg"}},
					"score":9.47,"genres":[{"mal_id":1,"type":"manga","name":"Action"},{"mal_id":8,"type":"manga","name":"Drama"}],
					"explicit_genres":[],"themes":[{"mal_id":58,"name":"Gore"}],"demographics":[{"mal_id":42,"name":"Seinen"}]},
				{"mal_id":104,"title":"Junjou Romantica","title_english":null,
					"images":{"jpg":{"image_url":"https://cdn.myanimelist.net/images/manga/2/1.jpg","large_image_url":null}},
					"score":null,"genres":[{"name":"Boys Love"},{"name":"Comedy"}],"explicit_genres":[{"name":"Erotica"}]},
				{"mal_id":2,"title":"Berserk duplicate"},
				{"mal_id":0,"title":"No id"},
				{"mal_id":5,"title":null,"title_english":null}
			]}
		""".trimIndent()
		val page = JikanDiscover.parse(body)
		assertTrue(page.hasNextPage)
		assertEquals(listOf("Berserk", "Junjou Romantica"), page.items.map { it.title })
		val berserk = page.items[0]
		assertEquals(-2, berserk.id)
		assertNull(berserk.alternativeTitle)
		assertEquals("https://cdn.myanimelist.net/images/manga/1/157897l.jpg", berserk.coverUrl)
		assertEquals(95, berserk.score)
		assertEquals(listOf("Action", "Drama", "Gore", "Seinen"), berserk.genres)
		assertFalse(berserk.isAdult)
		assertEquals("MyAnimeList", berserk.providerLabel)
		assertNull(berserk.aniListMediaId)
		assertNull(berserk.manga)
		val romantica = page.items[1]
		assertNull(romantica.score)
		assertEquals("https://cdn.myanimelist.net/images/manga/2/1.jpg", romantica.coverUrl)
		assertTrue(romantica.isAdult)
		assertTrue("Boys Love" in romantica.genres)
	}

	@Test
	fun englishTitleWinsAndAnimeRatingMarksAdult() {
		val page = JikanDiscover.parse("""
			{"pagination":{"has_next_page":false},"data":[
				{"mal_id":9,"title":"Shingeki no Kyojin","title_english":"Attack on Titan","score":8.5,"rating":"R - 17+ (violence & profanity)"},
				{"mal_id":10,"title":"Some OVA","rating":"Rx - Hentai","images":{"webp":{"large_image_url":"https://cdn/x.webp"}}}
			]}
		""".trimIndent())
		assertFalse(page.hasNextPage)
		assertEquals("Attack on Titan", page.items[0].title)
		assertEquals("Shingeki no Kyojin", page.items[0].alternativeTitle)
		assertFalse(page.items[0].isAdult)
		assertTrue(page.items[1].isAdult)
		assertEquals("https://cdn/x.webp", page.items[1].coverUrl)
	}

	@Test(expected = IOException::class)
	fun jikanErrorBodyIsReported() {
		JikanDiscover.parse("""{"status":429,"type":"RateLimitException","message":"You are being rate limited"}""")
	}

	@Test
	fun everyFeedMapsToAJikanListing() {
		AniListDiscover.Feed.entries.forEach { feed ->
			val url = JikanDiscover.buildUrl(feed, 2)
			assertEquals("api.jikan.moe", url.host)
			assertEquals("2", url.queryParameter("page"))
			assertEquals("25", url.queryParameter("limit"))
		}
		assertEquals("/v4/top/manga", JikanDiscover.buildUrl(AniListDiscover.Feed.TRENDING_MANGA, 1).encodedPath)
		assertEquals("/v4/seasons/now", JikanDiscover.buildUrl(AniListDiscover.Feed.TRENDING_ANIME, 1).encodedPath)
		val bl = JikanDiscover.buildUrl(AniListDiscover.Feed.POPULAR_BL, 1)
		assertEquals("/v4/manga", bl.encodedPath)
		assertEquals("28", bl.queryParameter("genres"))
		assertEquals("lightnovel", JikanDiscover.buildUrl(AniListDiscover.Feed.LIGHT_NOVELS, 1).queryParameter("type"))
		assertEquals("/v4/anime", JikanDiscover.buildUrl(AniListDiscover.Feed.BL_ANIME, 1).encodedPath)
	}
}
