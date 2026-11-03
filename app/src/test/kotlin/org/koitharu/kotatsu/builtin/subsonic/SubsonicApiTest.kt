package org.koitharu.kotatsu.builtin.subsonic

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class SubsonicApiTest {

	@Test
	fun md5MatchesTheSubsonicDocumentationExample() {
		// From the Subsonic API docs: password "sesame" with salt "c19b2d".
		assertEquals("26719a1196d2a940705a59634eb18eab", SubsonicApi.md5Hex("sesame" + "c19b2d"))
		assertEquals("d41d8cd98f00b204e9800998ecf8427e", SubsonicApi.md5Hex(""))
	}

	@Test
	fun authQueryUsesTokenAndSaltAndNeverThePassword() {
		val query = SubsonicApi.authQuery("joe", "sesame", "c19b2d")
		assertEquals("u=joe&t=26719a1196d2a940705a59634eb18eab&s=c19b2d&v=1.16.1&c=Monogatari&f=json", query)
		assertFalse(query.contains("sesame"))
		assertFalse(query.contains("p="))
	}

	@Test
	fun saltsAreFreshAndHex() {
		val salts = (1..20).map { SubsonicApi.newSalt() }
		assertEquals(20, salts.toSet().size)
		assertTrue(salts.all { it.matches(Regex("[0-9a-f]{16}")) })
	}

	@Test
	fun urlsCarryEncodedParameters() {
		val url = SubsonicApi.url(
			"https://music.example.com/nd", "search3", SubsonicApi.searchParams("fleetwood mac & co", 30),
			"joe smith", "sesame", "c19b2d",
		)
		assertTrue(url.startsWith("https://music.example.com/nd/rest/search3.view?u=joe%20smith&t="))
		assertTrue(url.contains("query=fleetwood%20mac%20%26%20co"))
		assertTrue(url.contains("albumOffset=30"))
		assertTrue(url.contains("songCount=0"))
		assertFalse(url.contains("sesame"))
		val list = SubsonicApi.url("http://h:4533", "getAlbumList2", SubsonicApi.albumListParams(false, 60), "u", "p", "s")
		assertTrue(list.contains("type=alphabeticalByName&size=30&offset=60"))
		assertTrue(SubsonicApi.albumListParams(true, -5).contains("type" to "newest"))
		assertTrue(SubsonicApi.albumListParams(true, -5).contains("offset" to "0"))
	}

	@Test
	fun parsesAnAlbumList() {
		val body = JSONObject(
			"""{"subsonic-response":{"status":"ok","version":"1.16.1","type":"navidrome","serverVersion":"0.53.3",
			"albumList2":{"album":[
			{"id":"al1","name":"Rumours","artist":"Fleetwood Mac","year":1977,"genre":"Rock","coverArt":"al-al1","songCount":11},
			{"id":"al2","name":"No Artist"},{"name":"No id"},{"id":"bad id!","name":"x"}]}}}""",
		)
		val albums = SubsonicApi.parseAlbumList(SubsonicApi.checkResponse(body))
		assertEquals(2, albums.size)
		assertEquals(SubsonicApi.Album("al1", "Rumours", "Fleetwood Mac", 1977, "Rock"), albums[0])
		assertNull(albums[1].artist)
		assertNull(albums[1].year)
	}

	@Test
	fun parsesSearchAndIgnoresMissingGroups() {
		val ok = JSONObject("""{"subsonic-response":{"status":"ok","searchResult3":{"album":[{"id":"a","name":"A"}]}}}""")
		assertEquals(listOf("a"), SubsonicApi.parseSearch(SubsonicApi.checkResponse(ok)).map { it.id })
		val empty = JSONObject("""{"subsonic-response":{"status":"ok","searchResult3":{}}}""")
		assertTrue(SubsonicApi.parseSearch(SubsonicApi.checkResponse(empty)).isEmpty())
	}

	@Test
	fun parsesAlbumDetailsOrderedByDiscAndTrack() {
		val body = JSONObject(
			"""{"subsonic-response":{"status":"ok","album":{"id":"al1","name":"Double","artist":"Band","year":2001,"song":[
			{"id":"s3","title":"Disc 2 Track 1","track":1,"discNumber":2,"duration":65},
			{"id":"s2","title":"Second","track":2,"discNumber":1,"duration":3725},
			{"id":"s1","title":"First","track":1,"discNumber":1,"duration":200},
			{"title":"No id"}]}}}""",
		)
		val details = SubsonicApi.parseAlbumDetails(SubsonicApi.checkResponse(body))!!
		assertEquals(listOf("s1", "s2", "s3"), details.songs.map { it.id })
		assertEquals("1 · First (3:20)", SubsonicApi.chapterTitle(details.songs[0], false, 0))
		assertEquals("1-2 · Second (1:02:05)", SubsonicApi.chapterTitle(details.songs[1], true, 1))
		assertEquals("2-1 · Disc 2 Track 1 (1:05)", SubsonicApi.chapterTitle(details.songs[2], true, 2))
		assertEquals("3 · Untracked", SubsonicApi.chapterTitle(SubsonicApi.Song("x", "Untracked", 0, 0, 0), false, 2))
	}

	@Test
	fun albumWithoutSongsHasNoChapters() {
		val body = JSONObject("""{"subsonic-response":{"status":"ok","album":{"id":"al1","name":"Empty"}}}""")
		assertTrue(SubsonicApi.parseAlbumDetails(SubsonicApi.checkResponse(body))!!.songs.isEmpty())
		assertNull(SubsonicApi.parseAlbumDetails(JSONObject("""{"status":"ok"}""")))
	}

	@Test
	fun failedStatusBecomesAPlainMessage() {
		val wrong = JSONObject("""{"subsonic-response":{"status":"failed","error":{"code":40,"message":"Wrong username or password"}}}""")
		try {
			SubsonicApi.checkResponse(wrong)
			fail("expected IOException")
		} catch (e: IOException) {
			assertTrue(e.message!!.contains("username or password"))
		}
		try {
			SubsonicApi.checkResponse(JSONObject("""{"hello":"world"}"""))
			fail("expected IOException")
		} catch (e: IOException) {
			assertEquals(SubsonicApi.NOT_SUBSONIC_MESSAGE, e.message)
		}
		assertNotNull(SubsonicApi.errorMessage(0, "boom"))
		assertTrue(SubsonicApi.errorMessage(0, "boom").endsWith("boom"))
		assertNull(SubsonicApi.httpErrorMessage(200))
		assertNotEquals(SubsonicApi.httpErrorMessage(404), SubsonicApi.httpErrorMessage(500))
	}

	@Test
	fun idsAreRestrictedBeforeTheyReachAUrl() {
		assertTrue(SubsonicApi.ID.matches("2bQp1xMy3JkVY0Zl9fTq8A"))
		assertTrue(SubsonicApi.ID.matches("al-12"))
		assertFalse(SubsonicApi.ID.matches("a/b"))
		assertFalse(SubsonicApi.ID.matches("a&b=c"))
	}
}
