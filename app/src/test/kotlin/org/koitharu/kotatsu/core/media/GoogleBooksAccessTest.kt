package org.koitharu.kotatsu.core.media

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleBooksAccessTest {

	private fun volume(access: String, sale: String = "NOT_FOR_SALE") =
		JSONObject("""{"accessInfo":$access,"saleInfo":{"saleability":"$sale"}}""")

	@Test
	fun freeEpubWithOfficialDownload() {
		val a = GoogleBooksAccess.describe(volume("""{"viewability":"ALL_PAGES","epub":{"isAvailable":true,"downloadLink":"https://books.google.com/e.epub"}}""", "FREE"))
		assertEquals("Free EPUB (official download)", a.label)
		assertTrue(a.freeToRead)
		assertEquals("https://books.google.com/e.epub", a.epubUrl)
	}

	@Test
	fun previewsAndListingsAreLabelledHonestly() {
		assertEquals("Preview of some pages (official)", GoogleBooksAccess.describe(volume("""{"viewability":"PARTIAL"}""", "FOR_SALE")).label)
		assertEquals("For sale: preview not offered", GoogleBooksAccess.describe(volume("""{"viewability":"NO_PAGES"}""", "FOR_SALE")).label)
		val none = GoogleBooksAccess.describe(JSONObject("{}"))
		assertEquals("Listing only", none.label)
		assertFalse(none.freeToRead)
		assertNull(none.epubUrl)
	}

	@Test
	fun nonHttpsDownloadLinksAreIgnored() {
		val a = GoogleBooksAccess.describe(volume("""{"viewability":"ALL_PAGES","epub":{"isAvailable":true,"downloadLink":"http://x/e.epub"}}"""))
		assertNull(a.epubUrl)
		assertEquals("Free to read in full (official viewer)", a.label)
	}
}
