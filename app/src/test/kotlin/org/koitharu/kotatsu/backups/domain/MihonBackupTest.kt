package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

class MihonBackupTest {

	/** Tiny protobuf writer for test fixtures. */
	private class Proto {
		val out = ByteArrayOutputStream()
		private fun varint(value: Long) {
			var v = value
			while (true) {
				if ((v and 0x7FL.inv()) == 0L) { out.write(v.toInt()); return }
				out.write(((v and 0x7F) or 0x80).toInt()); v = v ushr 7
			}
		}
		fun int(field: Int, value: Long) = apply { varint((field shl 3).toLong()); varint(value) }
		fun bytes(field: Int, value: ByteArray) = apply { varint(((field shl 3) or 2).toLong()); varint(value.size.toLong()); out.write(value) }
		fun str(field: Int, value: String) = bytes(field, value.toByteArray())
		fun msg(field: Int, value: Proto) = bytes(field, value.out.toByteArray())
		fun float(field: Int, value: Float) = apply {
			varint(((field shl 3) or 5).toLong())
			val bits = value.toRawBits()
			for (i in 0 until 4) out.write((bits ushr (8 * i)) and 0xFF)
		}
		fun build() = out.toByteArray()
	}

	private fun sample(): ByteArray {
		val chapter = Proto().str(1, "/ch/1").str(2, "Chapter 1").int(4, 1).int(6, 12).float(9, 1f)
		val unread = Proto().str(1, "/ch/2").str(2, "Chapter 2").float(9, 2f)
		val manga = Proto().int(1, 2499283573021220255).str(2, "/title/abc").str(3, "Frieren")
			.str(5, "Kanehito Yamada").str(7, "Fantasy").str(7, "Adventure")
			.msg(16, chapter).msg(16, unread)
			.bytes(17, byteArrayOf(0, 2)) // packed categories [0, 2]
			.int(100, 1)
			.msg(104, Proto().str(1, "/ch/1").int(2, 1_700_000_000_000))
			.str(9999, "a future field the decoder must skip")
		val other = Proto().int(1, 77).str(2, "/x").str(3, "Unknown source title").int(100, 0)
		return Proto().msg(1, manga).msg(1, other)
			.msg(2, Proto().str(1, "Reading").int(2, 0))
			.msg(101, Proto().str(1, "MangaDex (EN)").int(2, 2499283573021220255))
			.build()
	}

	@Test
	fun decodesLibraryChaptersHistoryAndSources() {
		val backup = MihonBackup.decode(sample())
		assertEquals(2, backup.manga.size)
		val frieren = backup.manga[0]
		assertEquals("Frieren", frieren.title)
		assertEquals("/title/abc", frieren.url)
		assertEquals("Kanehito Yamada", frieren.author)
		assertEquals(listOf("Fantasy", "Adventure"), frieren.genres)
		assertEquals(listOf(0L, 2L), frieren.categoryOrders)
		assertTrue(frieren.favorite)
		assertEquals(2, frieren.chapters.size)
		assertTrue(frieren.chapters[0].read)
		assertEquals(12L, frieren.chapters[0].lastPageRead)
		assertEquals(2f, frieren.chapters[1].number)
		assertEquals("/ch/1", frieren.history.single().chapterUrl)
		assertFalse(backup.manga[1].favorite)
		assertEquals("Reading", backup.categories.single().name)
		assertEquals("MangaDex (EN)", backup.sources[2499283573021220255])
	}

	@Test
	fun readsGzipFilesAndRejectsTruncatedData() {
		val gz = ByteArrayOutputStream().also { GZIPOutputStream(it).use { z -> z.write(sample()) } }.toByteArray()
		assertEquals(2, MihonBackup.read(ByteArrayInputStream(gz)).manga.size)
		val truncated = sample().copyOf(sample().size - 5)
		try {
			MihonBackup.decode(truncated)
			throw AssertionError("expected failure")
		} catch (_: IOException) {
		}
	}

	@Test
	fun summaryMatchesSourcesByUniqueNameIgnoringLanguageSuffix() {
		val summary = MihonImportSummary.of(MihonBackup.decode(sample()), mapOf("MangaDex" to "MANGADEX", "Comick" to "COMICK"))
		assertEquals(2, summary.titles)
		assertEquals(1, summary.favourites)
		assertEquals(1, summary.withProgress)
		assertEquals(mapOf("MangaDex (EN)" to "MANGADEX"), summary.matchedSources)
		assertEquals(listOf("Source 77"), summary.unmatchedSources)
		assertEquals(1, summary.matchedTitles)
	}

	@Test
	fun progressFollowsTheHighestReadChapterNumber() {
		val chapters = listOf(
			MihonBackup.Chapter("/1", "One", 1f, read = true, bookmark = false, lastPageRead = 0),
			MihonBackup.Chapter("/2", "Two", 2f, read = false, bookmark = false, lastPageRead = 7),
			MihonBackup.Chapter("/3", "Three", 3f, read = false, bookmark = false, lastPageRead = 0),
			MihonBackup.Chapter("/x", "Extra", -1f, read = true, bookmark = false, lastPageRead = 0),
		)
		assertEquals(2f, MihonProgress.lastReadNumber(chapters))
		assertEquals(null, MihonProgress.lastReadNumber(chapters.map { it.copy(read = false, lastPageRead = 0) }))
		assertEquals(2, MihonProgress.indexOfNumber(listOf(0.5f, 1f, 2f, 2f, 3f), 2f))
		assertEquals(-1, MihonProgress.indexOfNumber(listOf(1f, 3f), 2f))
	}
}
