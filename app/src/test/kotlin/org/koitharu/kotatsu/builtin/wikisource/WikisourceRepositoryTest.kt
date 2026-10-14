package org.koitharu.kotatsu.builtin.wikisource

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WikisourceRepositoryTest {

	@Test
	fun emptySearchBrowsesFeaturedTexts() {
		val url = WikisourceRepository.searchUrl("", 20)
		assertTrue(url.contains("Featured%20texts"))
		assertTrue(url.contains("sroffset=20"))
		assertTrue(WikisourceRepository.searchUrl("pride & prejudice", 0).contains("pride%20%26%20prejudice"))
	}

	@Test
	fun parsesSearchHitsAndStripsSnippetMarkup() {
		val body = JSONObject(
			"""{"query":{"search":[
				{"title":"Pride and Prejudice","snippet":"It is a <span class=\"searchmatch\">truth</span> &quot;universally&quot; acknowledged"},
				{"title":"","snippet":"x"}
			]}}""",
		)
		val hits = WikisourceRepository.parseSearch(body)
		assertEquals(1, hits.size)
		assertEquals("Pride and Prejudice", hits[0].title)
		assertEquals("It is a truth \"universally\" acknowledged", hits[0].snippet)
	}

	@Test(expected = IOException::class)
	fun apiErrorsSurface() {
		WikisourceRepository.parseSearch(JSONObject("""{"error":{"code":"x","info":"Search is down"}}"""))
	}

	@Test
	fun subpagesSortNaturally() {
		val body = JSONObject(
			"""{"query":{"allpages":[
				{"title":"Novel/Chapter 10"},{"title":"Novel/Chapter 2"},{"title":"Novel/Chapter 1"},{"title":"Other/Chapter 1"}
			]}}""",
		)
		assertEquals(
			listOf("Novel/Chapter 1", "Novel/Chapter 2", "Novel/Chapter 10"),
			WikisourceRepository.parseSubpages(body, "Novel"),
		)
	}

	@Test
	fun publicUrlKeepsSlashesAndUnderscores() {
		assertEquals(
			"https://en.wikisource.org/wiki/Alice%27s_Adventures/Chapter_1",
			WikisourceRepository.publicUrl("Alice's Adventures/Chapter 1"),
		)
		assertEquals("Moby-Dick", WikisourceRepository.titleOf("/wiki/Moby-Dick"))
	}

	@Test
	fun parsesPageHtml() {
		assertEquals("<p>Hi</p>", WikisourceRepository.parseHtml(JSONObject("""{"parse":{"title":"T","text":"<p>Hi</p>"}}""")))
		assertEquals("", WikisourceRepository.parseHtml(JSONObject("{}")))
	}
}
