package org.koitharu.kotatsu.builtin.plex

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlexRepositoryTest {

	@Test
	fun parsesRecentAndSearchHubsKeepingOnlyMoviesAndShows() {
		val recent = JSONObject("""{"MediaContainer":{"Metadata":[
			{"ratingKey":"12","type":"movie","title":"Heat","year":1995,"summary":"s"},
			{"ratingKey":"13","type":"track","title":"Song"},
			{"ratingKey":"x","type":"movie","title":"Bad id"}]}}""")
		assertEquals(listOf("Heat"), PlexRepository.parseItems(recent).map { it.title })
		val hubs = JSONObject("""{"MediaContainer":{"Hub":[
			{"type":"show","Metadata":[{"ratingKey":"40","type":"show","title":"Cowboy Bebop"}]},
			{"type":"movie","Metadata":[{"ratingKey":"12","type":"movie","title":"Heat"},{"ratingKey":"12","type":"movie","title":"Heat"}]}]}}""")
		assertEquals(listOf("Cowboy Bebop", "Heat"), PlexRepository.parseItems(hubs).map { it.title })
	}

	@Test
	fun episodesAreOrderedAndNeedPlayableParts() {
		val body = JSONObject("""{"MediaContainer":{"Metadata":[
			{"title":"Two","parentIndex":1,"index":2,"Media":[{"Part":[{"key":"/library/parts/2/1/b.mkv"}]}]},
			{"title":"One","parentIndex":1,"index":1,"Media":[{"Part":[{"key":"/library/parts/1/1/a.mkv"}]}]},
			{"title":"NoFile","parentIndex":1,"index":3}]}}""")
		val episodes = PlexRepository.parseEpisodes(body)
		assertEquals(listOf("S1 E1 · One", "S1 E2 · Two"), episodes.map { it.title })
		assertEquals("/library/parts/1/1/a.mkv", episodes[0].partKey)
	}

	@Test
	fun movieFilePartAndUrlHelpers() {
		val movie = JSONObject("""{"MediaContainer":{"Metadata":[{"Media":[{"Part":[{"key":"/library/parts/9/9/m.mp4"}]}]}]}}""")
		assertEquals("/library/parts/9/9/m.mp4", PlexRepository.partKey(movie))
		assertNull(PlexRepository.partKey(JSONObject("""{"MediaContainer":{"Metadata":[{"Media":[{"Part":[{"key":"http://evil/x"}]}]}]}}""")))
		assertEquals("show" to "40", PlexRepository.splitUrl("/show/40"))
		assertNull(PlexRepository.splitUrl("/track/40"))
		assertTrue(PlexRepository.searchUrl("http://h:32400", "cowboy bebop").contains("query=cowboy%20bebop"))
	}
}
