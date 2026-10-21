package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.koitharu.kotatsu.core.media.LocalFileImporter.Kind

class LocalFileImporterTest {

	@Test
	fun booksByExtension() {
		for (name in listOf("A.epub", "b.PDF", "c.txt", "d.md", "e.markdown", "f.html", "g.htm", "h.xhtml", "i.text")) {
			assertEquals(name, Kind.BOOK, LocalFileImporter.classify(name, null))
		}
	}

	@Test
	fun comicsOnlyInFormatsTheLocalImportReads() {
		assertEquals(Kind.COMIC, LocalFileImporter.classify("Vol 1.cbz", "application/octet-stream"))
		assertEquals(Kind.COMIC, LocalFileImporter.classify("chapter.ZIP", "application/zip"))
		assertNull(LocalFileImporter.classify("Vol 2.cbr", "application/x-cbr"))
		assertNull(LocalFileImporter.classify("page.jpg", "image/jpeg"))
		// The importer needs a .cbz/.zip name, so a zip MIME type alone is not enough.
		assertNull(LocalFileImporter.classify("archive", "application/zip"))
	}

	@Test
	fun videoAndAudioByExtension() {
		for (name in listOf("a.mp4", "b.mkv", "c.webm", "d.m4v", "e.mov", "f.3gp")) {
			assertEquals(name, Kind.VIDEO, LocalFileImporter.classify(name, null))
		}
		for (name in listOf("a.mp3", "b.m4a", "c.m4b", "d.aac", "e.ogg", "f.opus", "g.flac", "h.wav")) {
			assertEquals(name, Kind.AUDIO, LocalFileImporter.classify(name, "application/octet-stream"))
		}
	}

	@Test
	fun containerTypeFollowsProviderMime() {
		assertEquals(Kind.AUDIO, LocalFileImporter.classify("song.mp4", "audio/mp4"))
		assertEquals(Kind.AUDIO, LocalFileImporter.classify("talk.webm", "audio/webm"))
		assertEquals(Kind.VIDEO, LocalFileImporter.classify("clip.ogg", "video/ogg"))
	}

	@Test
	fun mimeFallback() {
		assertEquals(Kind.VIDEO, LocalFileImporter.classify("recording", "video/mp4"))
		assertEquals(Kind.AUDIO, LocalFileImporter.classify("memo.amr", "audio/amr"))
		assertEquals(Kind.BOOK, LocalFileImporter.classify("novel", "application/epub+zip"))
		assertEquals(Kind.BOOK, LocalFileImporter.classify("notes", "text/plain; charset=utf-8"))
		// A known non-book extension is not turned into a book by a generic text type.
		assertNull(LocalFileImporter.classify("data.json", "text/plain"))
		assertNull(LocalFileImporter.classify("setup.apk", "application/vnd.android.package-archive"))
		assertNull(LocalFileImporter.classify(null, null))
	}

	@Test
	fun bookFileNameAddsExtensionFromMime() {
		assertEquals("Story.epub", LocalFileImporter.bookFileName("Story.epub", "application/epub+zip"))
		assertEquals("novel.epub", LocalFileImporter.bookFileName("novel", "application/epub+zip"))
		assertEquals("notes.txt", LocalFileImporter.bookFileName("notes", "text/plain"))
		assertEquals("unknown", LocalFileImporter.bookFileName("unknown", null))
	}

	@Test
	fun positionFormatting() {
		assertEquals("0:00", LocalFileImporter.formatPosition(0))
		assertEquals("1:05", LocalFileImporter.formatPosition(65_000))
		assertEquals("1:02:03", LocalFileImporter.formatPosition(3_723_000))
	}
}
