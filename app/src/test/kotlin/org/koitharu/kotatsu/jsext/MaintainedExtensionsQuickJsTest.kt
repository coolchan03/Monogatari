package org.koitharu.kotatsu.jsext

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeNoException
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * Runs Monogatari's own SkyMangas and Rocks Manga extensions inside the real QuickJS host (not Node) through
 * search -> details -> chapters -> pages, against canned API responses shaped like the documented endpoints.
 *
 * The fixtures are written from the extensions' own endpoint notes, NOT recorded from the live sites, so a pass
 * proves the extension logic and host bridge work, not that the websites still answer today.
 */
class MaintainedExtensionsQuickJsTest {

	private val requests = ArrayList<JsHttpRequest>()
	private val store = object : JsPreferenceStore {
		private val data = HashMap<String, String>()
		override fun getString(key: String) = data[key]
		override fun setString(key: String, value: String) {
			data[key] = value
		}
	}

	@Before
	fun requireNativeQuickJs() {
		try {
			com.dokar.quickjs.QuickJs.create(kotlinx.coroutines.Dispatchers.Default).close()
		} catch (e: Throwable) {
			assumeNoException("QuickJS native library is not loadable on this JVM", e)
		}
	}

	private fun script(relative: String): String {
		val file = listOf(File("../extensions/javascript/manga/src/$relative"), File("extensions/javascript/manga/src/$relative"))
			.firstOrNull { it.isFile }
		assumeTrue("extension script $relative not found from ${File(".").absolutePath}", file != null)
		return file!!.readText()
	}

	private fun transport(routes: Map<String, Pair<Int, String>>) = JsHttpTransport { request ->
		requests += request
		val hit = routes.entries.firstOrNull { request.url == it.key }
		if (hit == null) JsHttpResponse(404, "Not Found", emptyMap(), "{\"message\":\"nope\"}", request.url)
		else JsHttpResponse(hit.value.first, null, mapOf("content-type" to "application/json"), hit.value.second, request.url)
	}

	// ---- SkyMangas ------------------------------------------------------------------------------------------

	private val skyInfo = JsSourceInfo(
		id = 4720001001L, name = "SkyMangas", baseUrl = "https://skymangas.com", lang = "es",
		apiUrl = "https://api.skymangas.com/api/v1", itemType = JsItemType.MANGA,
	)
	private val skyApi = "https://api.skymangas.com/api/v1"

	private fun sky(routes: Map<String, Pair<Int, String>>) =
		JsExtension(skyInfo, script("es/skymangas.js"), transport(routes), store) { }

	@Test
	fun skyMangasSearchDetailsChaptersAndPagesWorkEndToEnd() = runBlocking {
		val routes = mapOf(
			"$skyApi/manhuas/search?q=sky&page=1&perPage=20&availability=readable" to (200 to """
				{"success":true,"data":[
				  {"slug":"sky-one","title":"Sky One","coverThumbUrl":"/img/one.jpg","availability":"readable"},
				  {"slug":"catalog-only","title":"No Chapters","availability":"catalog_only"}
				],"meta":{"page":1,"totalPages":2}}"""),
			"$skyApi/manhuas/sky-one" to (200 to """
				{"success":true,"data":{"id":77,"title":"Sky One","slug":"sky-one","coverUrl":"/img/one-large.jpg",
				 "synopsis":"A story.","author":"A. Writer","artist":"B. Artist","statusId":1,
				 "genres":[{"name":"Action"}],"tags":[{"name":"Magic"}],"originationName":"Manhua","isReadable":true}}"""),
			"$skyApi/chapters/manhua/77?page=1&perPage=100&sort=desc" to (200 to """
				{"success":true,"data":[
				  {"id":902,"chapterNumber":2,"title":"Second","publishedAt":"2026-11-02T10:00:00Z","groupNames":"Team"},
				  {"id":901,"chapterNumber":1,"publishedAt":"2026-11-01T10:00:00Z"},
				  {"id":901,"chapterNumber":1}
				],"meta":{"totalPages":1}}"""),
			"$skyApi/chapters/902/pages" to (200 to """
				{"success":true,"data":[
				  {"pageNumber":2,"imageUrl":"https://cdn.test/p2.webp?token=b"},
				  {"pageNumber":1,"imageUrl":"/p1.webp?token=a"},
				  {"pageNumber":3}
				]}"""),
		)
		sky(routes).use { ext ->
			val found = ext.search("sky", 1)
			assertEquals(listOf("Sky One"), found.list.map { it.name })
			assertEquals("https://api.skymangas.com/img/one.jpg", found.list.single().imageUrl)
			assertTrue("more result pages exist", found.hasNextPage)

			val manga = ext.getDetail(found.list.single().link!!)
			assertEquals("Sky One", manga.name)
			assertEquals("A. Writer", manga.author)
			assertEquals(0, manga.status)
			assertEquals(listOf("Manhua", "Action", "Magic"), manga.genre)
			assertEquals(listOf("Capítulo 2: Second", "Capítulo 1"), manga.chapters.map { it.name })
			assertEquals("chapters/902", manga.chapters[0].url)
			assertEquals("Team", manga.chapters[0].scanlator)

			val pages = ext.getPageList(manga.chapters[0].url!!)
			assertEquals(
				listOf("https://api.skymangas.com/p1.webp?token=a", "https://cdn.test/p2.webp?token=b"),
				pages.map { it.url },
			)
			assertEquals("https://skymangas.com/", pages[0].headers?.get("Referer"))
		}
		// Every request carried the site as Origin/Referer, like the site's own client.
		assertTrue(requests.all { it.headers["Origin"] == "https://skymangas.com" })
	}

	@Test
	fun skyMangasReportsAnErrorInsteadOfLookingEmptyWhenTheApiSendsHtml() = runBlocking {
		val routes = mapOf("$skyApi/manhuas/trending?page=1&perPage=20" to (200 to "<html>Just a moment...</html>"))
		sky(routes).use { ext ->
			try {
				ext.getPopular(1)
				fail("a challenge page must not be reported as an empty catalog")
			} catch (e: JsExtensionException) {
				assertFalse(e.message.isNullOrBlank())
			}
		}
	}

	@Test
	fun skyMangasApiFailureMessageIsSurfaced() = runBlocking {
		val routes = mapOf("$skyApi/manhuas/latest?page=1&perPage=20" to (200 to """{"success":false,"message":"rate limited"}"""))
		sky(routes).use { ext ->
			try {
				ext.getLatestUpdates(1)
				fail("expected failure")
			} catch (e: JsExtensionException) {
				assertTrue(e.message.orEmpty().contains("rate limited"))
			}
		}
	}

	// ---- Rocks Manga ----------------------------------------------------------------------------------------

	private val rocksInfo = JsSourceInfo(
		id = 4720001002L, name = "Rocks Manga", baseUrl = "https://rocksmanga.com", lang = "ar",
		apiUrl = "https://api.rocksmanga.com", itemType = JsItemType.MANGA,
	)
	private val rocksApi = "https://api.rocksmanga.com"

	private fun rocks(routes: Map<String, Pair<Int, String>>) =
		JsExtension(rocksInfo, script("ar/rocksmanga.js"), transport(routes), store) { }

	@Test
	fun rocksMangaSearchDetailsChaptersAndPagesWorkEndToEnd() = runBlocking {
		val routes = mapOf(
			"$rocksApi/manga?page=1&search=rock" to (200 to """
				{"items":[{"slug":"rock-one","title":"Rock One","cover_url":"/c/one.jpg"},{"title":"no slug"}],
				 "total":45,"page":1,"pageSize":20}"""),
			"$rocksApi/manga/rock-one" to (200 to """
				{"manga":{"slug":"rock-one","title":"Rock One","cover_url":"https://cdn.test/c.jpg","synopsis":"قصة",
				 "status":"COMPLETED","type":"Manga","authors":[{"name":"Au"}],"artists":["Ar"],"genres":[{"name":"Drama"}]}}"""),
			"$rocksApi/manga/rock-one/chapters" to (200 to """
				{"chapters":[
				  {"number":1,"created_at":"2026-10-01T00:00:00Z"},
				  {"number":10,"title":"Ten","created_at":"2026-10-10T00:00:00Z","uploader":{"display_name":"Grp"}},
				  {"number":2,"created_at":"2026-10-02T00:00:00Z"},
				  {"number":2}
				]}"""),
			"$rocksApi/manga/rock-one/chapter/10" to (200 to """
				{"chapter":{"pages":["/p/1.jpg",{"url":"https://cdn.test/p/2.jpg"}]}}"""),
		)
		rocks(routes).use { ext ->
			val found = ext.search("rock", 1)
			assertEquals(listOf("Rock One"), found.list.map { it.name })
			assertEquals("https://api.rocksmanga.com/c/one.jpg", found.list.single().imageUrl)
			assertTrue(found.hasNextPage)

			val manga = ext.getDetail("/rock-one")
			assertEquals(1, manga.status)
			assertEquals(listOf("Manga", "Drama"), manga.genre)
			assertEquals("Au", manga.author)
			// Numeric, newest first; the duplicate chapter number is dropped.
			assertEquals(listOf("rock-one/chapter/10", "rock-one/chapter/2", "rock-one/chapter/1"), manga.chapters.map { it.url })
			assertEquals("Grp", manga.chapters[0].scanlator)

			val pages = ext.getPageList("rock-one/chapter/10")
			assertEquals(listOf("https://api.rocksmanga.com/p/1.jpg", "https://cdn.test/p/2.jpg"), pages.map { it.url })
		}
	}

	@Test
	fun rocksMangaHttpErrorsAreNotMistakenForEmptyResults() = runBlocking {
		val routes = mapOf("$rocksApi/manga?page=1" to (429 to "{\"error\":\"slow down\"}"))
		rocks(routes).use { ext ->
			try {
				ext.getPopular(1)
				fail("HTTP 429 must surface as an error")
			} catch (e: JsExtensionException) {
				assertTrue(e.message.orEmpty().contains("429"))
			}
		}
	}

	@Test
	fun rocksMangaSecondPageOfSmallCatalogHasNoNextPage() = runBlocking {
		val routes = mapOf("$rocksApi/manga?page=2" to (200 to """{"items":[{"slug":"a","title":"A"}],"total":21,"page":2,"pageSize":20}"""))
		rocks(routes).use { ext -> assertFalse(ext.getPopular(2).hasNextPage) }
	}
}
