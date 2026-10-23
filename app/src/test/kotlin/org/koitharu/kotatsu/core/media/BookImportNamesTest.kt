package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BookImportNamesTest {

	@Test
	fun longNamesKeepTheirExtension() {
		val name = "A".repeat(300) + ".epub"
		val safe = LocalFileImporter.safeBookName(name)
		assertTrue(safe, safe.endsWith(".epub"))
		assertTrue(BookShelf.isShelfFile("book-123-$safe"))
		assertTrue(("book-9223372036854775807-$safe").toByteArray(Charsets.UTF_8).size <= 255)
	}

	@Test
	fun lettersOfAnyScriptStayReadable() {
		assertEquals("物語 第1巻.epub", LocalFileImporter.safeBookName("物語 第1巻.epub"))
		assertEquals("Ça va_ oui.pdf", LocalFileImporter.safeBookName("Ça va? oui.PDF"))
		val long = LocalFileImporter.safeBookName("物".repeat(200) + ".txt")
		assertTrue(long.endsWith(".txt"))
		assertTrue(("book-9223372036854775807-$long").toByteArray(Charsets.UTF_8).size <= 255)
	}

	@Test
	fun blankNamesGetAFallback() {
		assertEquals("book.epub", LocalFileImporter.safeBookName(".epub"))
		assertEquals("book.pdf", LocalFileImporter.safeBookName("   .pdf"))
		assertEquals("___.pdf", LocalFileImporter.safeBookName("???.pdf"))
	}

	@Test
	fun shelfListsOnlyBookFormats() {
		assertTrue(BookShelf.isShelfFile("book-1-Dune.EPUB"))
		assertTrue(BookShelf.isShelfFile("notes.md"))
		assertFalse(BookShelf.isShelfFile("book-1-.partial"))
		assertFalse(BookShelf.isShelfFile("book-1-Long title.ep"))
	}

	@Test
	fun sniffsTruncatedBooks() {
		val pdf = "%PDF-1.7\n".toByteArray(Charsets.ISO_8859_1)
		assertEquals("pdf", BookShelf.sniffExtension(pdf))
		val epub = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(26) + "mimetypeapplication/epub+zip".toByteArray(Charsets.ISO_8859_1)
		assertEquals("epub", BookShelf.sniffExtension(epub))
		val zip = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(40)
		assertNull(BookShelf.sniffExtension(zip))
		assertNull(BookShelf.sniffExtension(ByteArray(0)))
	}

	@Test
	fun repairedNamesDropTheCutOffExtension() {
		assertEquals("book-1-Long title.epub", BookShelf.repairedName("book-1-Long title.ep", "epub"))
		assertEquals("book-1-Long title.e.pdf", BookShelf.repairedName("book-1-Long title.e", "pdf"))
		assertEquals("book-1-Vol. 2.epub", BookShelf.repairedName("book-1-Vol. 2", "epub"))
		assertEquals("Vol 2", BookShelf.title(File(BookShelf.repairedName("book-55-Vol 2.e", "epub"))))
	}
}
