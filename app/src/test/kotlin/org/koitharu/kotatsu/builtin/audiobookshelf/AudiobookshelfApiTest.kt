package org.koitharu.kotatsu.builtin.audiobookshelf

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudiobookshelfApiTest {

	@Test
	fun buildsUrlsWithoutTheToken() {
		val s = "https://abs.example.com"
		assertEquals("$s/ping", AudiobookshelfApi.pingUrl(s))
		assertEquals("$s/api/libraries", AudiobookshelfApi.librariesUrl(s))
		val items = AudiobookshelfApi.itemsUrl(s, "lib_1", 2)
		assertTrue(items.startsWith("$s/api/libraries/lib_1/items?"))
		assertTrue(items.contains("page=2"))
		assertTrue(items.contains("sort=addedAt&desc=1"))
		assertTrue(AudiobookshelfApi.itemsUrl(s, "lib_1", -3).contains("page=0"))
		assertEquals("$s/api/libraries/lib_1/search?limit=30&q=the%20hobbit", AudiobookshelfApi.searchUrl(s, "lib_1", "the hobbit"))
		assertEquals("$s/api/items/li_1?expanded=1", AudiobookshelfApi.itemUrl(s, "li_1"))
		assertEquals("$s/api/items/li_1/file/1234", AudiobookshelfApi.fileUrl(s, "li_1", "1234"))
		assertEquals("Bearer tok", AudiobookshelfApi.authHeaders("tok")["Authorization"])
	}

	@Test
	fun parsesLibrariesAndSkipsUnsupportedTypes() {
		val body = JSONObject(
			"""{"libraries":[{"id":"lib_a","name":"Books","mediaType":"book"},
			{"id":"lib_p","name":"Pods","mediaType":"podcast"},{"id":"lib_x","name":"Other","mediaType":"music"},
			{"name":"no id","mediaType":"book"}]}""",
		)
		val libraries = AudiobookshelfApi.parseLibraries(body)
		assertEquals(listOf("lib_a", "lib_p"), libraries.map { it.id })
		assertFalse(libraries[0].isPodcast)
		assertTrue(libraries[1].isPodcast)
	}

	@Test
	fun parsesMinifiedItems() {
		val body = JSONObject(
			"""{"results":[
			{"id":"li_1","media":{"metadata":{"title":"Dune","authorName":"Frank Herbert","narratorName":"Scott Brick","genres":["SciFi","Classic"],"description":"Spice."}}},
			{"id":"li_2","media":{"metadata":{"title":"A Podcast","author":"Host"}}},
			{"id":"li_3","media":{"metadata":{"title":""}}},{"media":{"metadata":{"title":"No id"}}}],"total":2}""",
		)
		val items = AudiobookshelfApi.parseItems(body)
		assertEquals(2, items.size)
		assertEquals("Dune", items[0].title)
		assertEquals("Frank Herbert", items[0].author)
		assertEquals("Scott Brick", items[0].narrator)
		assertEquals(listOf("SciFi", "Classic"), items[0].genres)
		assertEquals("Host", items[1].author)
		assertNull(items[1].description)
	}

	@Test
	fun parsesSearchGroupsAndDeduplicates() {
		val body = JSONObject(
			"""{"book":[{"libraryItem":{"id":"li_1","media":{"metadata":{"title":"Dune"}}}}],
			"podcast":[{"libraryItem":{"id":"li_2","media":{"metadata":{"title":"Pod"}}}},
			{"libraryItem":{"id":"li_1","media":{"metadata":{"title":"Dune"}}}}],"authors":[]}""",
		)
		assertEquals(listOf("li_1", "li_2"), AudiobookshelfApi.parseSearch(body).map { it.id })
		assertTrue(AudiobookshelfApi.parseSearch(JSONObject("{}")).isEmpty())
	}

	@Test
	fun parsesABookWithSeveralAudioFilesInOrder() {
		val body = JSONObject(
			"""{"id":"li_1","mediaType":"book","media":{"metadata":{"title":"Dune","authors":[{"id":"a","name":"Frank Herbert"}],
			"narrators":["Scott Brick"],"genres":["SciFi"],"description":"Spice."},
			"audioFiles":[
			{"index":2,"ino":"22","metadata":{"filename":"02 Book Two.mp3"}},
			{"index":1,"ino":"11","metadata":{"filename":"01 Book One.mp3"},"metaTags":{"tagTitle":"Book One"}},
			{"index":3,"ino":"33","exclude":true},{"index":4,"ino":"44","invalid":true},{"index":5}]}}""",
		)
		val details = AudiobookshelfApi.parseDetails(body)!!
		assertFalse(details.isPodcast)
		assertEquals(listOf("Frank Herbert"), details.authors)
		assertEquals("Scott Brick", details.item.narrator)
		assertEquals(listOf("11", "22"), details.tracks.map { it.ino })
		assertEquals(listOf("Book One", "02 Book Two"), details.tracks.map { it.title })
		assertEquals(listOf(1f, 2f), details.tracks.map { it.number })
	}

	@Test
	fun aSingleFileBookIsOneChapterNamedAfterTheBook() {
		val body = JSONObject(
			"""{"id":"li_1","mediaType":"book","media":{"metadata":{"title":"Short"},"audioFiles":[{"index":1,"ino":"9","metadata":{"filename":"x.m4b"}}]}}""",
		)
		assertEquals(listOf("Short"), AudiobookshelfApi.parseDetails(body)!!.tracks.map { it.title })
	}

	@Test
	fun parsesPodcastEpisodesOldestFirst() {
		val body = JSONObject(
			"""{"id":"li_p","mediaType":"podcast","media":{"metadata":{"title":"Pod","author":"Host"},"episodes":[
			{"id":"e2","title":"Newer","publishedAt":2000,"audioFile":{"ino":"202"}},
			{"id":"e1","title":"","publishedAt":1000,"audioFile":{"ino":"101"}},
			{"id":"e0","title":"No file","publishedAt":500}]}}""",
		)
		val details = AudiobookshelfApi.parseDetails(body)!!
		assertTrue(details.isPodcast)
		assertEquals(listOf("101", "202"), details.tracks.map { it.ino })
		assertEquals(listOf("Episode 1", "Newer"), details.tracks.map { it.title })
		assertEquals(listOf(1000L, 2000L), details.tracks.map { it.publishedAt })
		assertEquals(listOf("Host"), details.authors)
	}

	@Test
	fun idsAreRestrictedAndErrorsAreReadable() {
		assertTrue(AudiobookshelfApi.ID.matches("li_8gj2n1b9w6u3v5x7z0"))
		assertFalse(AudiobookshelfApi.ID.matches("li/../x"))
		assertFalse(AudiobookshelfApi.ID.matches("a?b"))
		assertNull(AudiobookshelfApi.httpErrorMessage(200))
		assertTrue(AudiobookshelfApi.httpErrorMessage(401)!!.contains("token"))
		assertTrue(AudiobookshelfApi.httpErrorMessage(502)!!.contains("502"))
		assertNull(AudiobookshelfApi.parseDetails(JSONObject("{}")))
	}
}
