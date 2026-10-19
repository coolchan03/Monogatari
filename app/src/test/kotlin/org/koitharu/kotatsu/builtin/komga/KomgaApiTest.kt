package org.koitharu.kotatsu.builtin.komga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KomgaApiTest {

	@Test
	fun buildsUrlsWithoutCredentialsAndRejectsUnsafeIds() {
		assertEquals(
			"https://komga.local/api/v1/series?search=one+piece&page=0&size=30&sort=metadata.titleSort,asc",
			KomgaApi.searchSeriesUrl("https://komga.local/", "One Piece".lowercase(), 0),
		)
		assertEquals("https://komga.local/api/v1/books/0ABC/pages/3", KomgaApi.pageImageUrl("https://komga.local", "0ABC", 3))
		assertTrue(runCatching { KomgaApi.booksUrl("https://komga.local", "../admin", 0) }.isFailure)
	}

	@Test
	fun parsesSeriesAndBooksPages() {
		val series = KomgaApi.parseSeries("""{"content":[
			{"id":"0A1","name":"folder","booksCount":12,"metadata":{"title":"Berserk","summary":"Guts.","genres":["Dark Fantasy"]}},
			{"id":"bad id","metadata":{"title":"Skipped"}}],"number":0,"last":false}""")
		assertEquals(listOf("Berserk"), series.items.map { it.title })
		assertEquals(listOf("Dark Fantasy"), series.items.single().genres)
		assertEquals(false, series.isLast)
		val books = KomgaApi.parseBooks("""{"content":[{"id":"B1","name":"v01.cbz","metadata":{"title":"Volume 1","number":"1"},
			"media":{"pagesCount":220,"mediaType":"application/zip"}}],"number":0,"last":true}""")
		assertEquals(1f, books.items.single().number)
		assertEquals(220, books.items.single().pagesCount)
	}
}
