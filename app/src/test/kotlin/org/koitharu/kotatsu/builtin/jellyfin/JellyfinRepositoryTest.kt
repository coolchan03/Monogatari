package org.koitharu.kotatsu.builtin.jellyfin

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JellyfinRepositoryTest {

	private val id1 = "0123456789abcdef0123456789abcdef"
	private val id2 = "fedcba9876543210fedcba9876543210"

	@Test
	fun normalizesWhatPeopleType() {
		assertEquals("http://192.168.1.5:8096", JellyfinRepository.normalizeServer(" 192.168.1.5:8096/ "))
		assertEquals("http://nas.local:8096", JellyfinRepository.normalizeServer("nas.local:8096"))
		assertEquals("http://jellyfin", JellyfinRepository.normalizeServer("jellyfin"))
		assertEquals("https://media.example.com", JellyfinRepository.normalizeServer("media.example.com"))
		assertEquals("https://media.example.com/jf", JellyfinRepository.normalizeServer("https://media.example.com/jf/"))
		assertNull(JellyfinRepository.normalizeServer(""))
		assertNull(JellyfinRepository.normalizeServer("ftp://host"))
		assertNull(JellyfinRepository.normalizeServer("has space.com"))
		assertNull(JellyfinRepository.normalizeServer("http://"))
	}

	@Test
	fun itemsUrlSearchesAndPages() {
		val url = JellyfinRepository.itemsUrl("http://h:8096", "cowboy bebop", 30, newestFirst = false)
		assertTrue(url.startsWith("http://h:8096/Items?"))
		assertTrue(url.contains("SearchTerm=cowboy%20bebop"))
		assertTrue(url.contains("StartIndex=30"))
		assertTrue(url.contains("SortBy=SortName"))
		assertTrue(JellyfinRepository.itemsUrl("http://h", "", 0, newestFirst = true).contains("SortBy=DateCreated&SortOrder=Descending"))
	}

	@Test
	fun streamUrlNeverContainsTheKey() {
		assertEquals("http://h/Videos/$id1/stream?static=true", JellyfinRepository.streamUrl("http://h", id1))
	}

	@Test
	fun parsesItemsAndSkipsBadIds() {
		val body = JSONObject(
			"""{"Items":[
				{"Id":"$id1","Name":"Movie","ProductionYear":1998,"Overview":"o","ImageTags":{"Primary":"x"}},
				{"Id":"nope","Name":"Bad"},
				{"Id":"$id2","Name":""}
			]}""",
		)
		val items = JellyfinRepository.parseItems(body)
		assertEquals(1, items.size)
		assertEquals(1998, items[0].year)
		assertTrue(items[0].hasImage)
	}

	@Test
	fun episodesAreOrderedAndLabelled() {
		val body = JSONObject(
			"""{"Items":[
				{"Id":"$id2","Name":"Second","ParentIndexNumber":1,"IndexNumber":2},
				{"Id":"$id1","Name":"First","ParentIndexNumber":1,"IndexNumber":1}
			]}""",
		)
		val episodes = JellyfinRepository.parseEpisodes(body)
		assertEquals(listOf("S1 E1 · First", "S1 E2 · Second"), episodes.map { it.title })
		assertEquals(listOf(1f, 2f), episodes.map { it.number })
	}
}
