package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.jsext.source.jsUid
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MangayomiBackupTest {

	private val sample = """
		{"version":"2",
		 "manga":[
		  {"id":1,"name":"Given","link":"/manga/given","imageUrl":"https://img/given.jpg","source":"MangaDex","lang":"en",
		   "itemType":0,"favorite":true,"categories":[10],"author":"Natsuki Kizu","genre":["BL","Music"],"status":1,"dateAdded":1000},
		  {"id":2,"name":"Old Anime","link":"/anime/2","source":"AnimeSite","lang":"en","isManga":false,"favorite":true,"categories":[]},
		  {"id":3,"name":"Only read once","link":"/m/3","source":"Unknown Site","lang":"en","favorite":false},
		  {"id":4,"name":"Never opened","link":"/m/4","source":"MangaDex","lang":"en","favorite":false},
		  {"id":5,"name":"","link":"/m/5","source":"MangaDex","lang":"en","favorite":true}
		 ],
		 "chapters":[
		  {"id":100,"mangaId":1,"url":"/c/1","name":"Ch 1","isRead":true,"lastPageRead":"12","dateUpload":"10"},
		  {"id":101,"mangaId":1,"url":"/c/2","name":"Ch 2","isRead":false,"lastPageRead":"5","dateUpload":"20"},
		  {"id":102,"mangaId":1,"url":"/c/3","name":"Ch 3","isRead":false,"lastPageRead":"0","dateUpload":"30"},
		  {"id":103,"mangaId":1,"url":"/c/4","name":"Ch 4","isRead":true,"lastPageRead":"0","dateUpload":"40"},
		  {"id":300,"mangaId":3,"url":"/c/300","name":"Ch","isRead":true,"lastPageRead":"3"}
		 ],
		 "categories":[{"id":10,"name":"BL","forItemType":0,"pos":1}],
		 "history":[{"id":1,"mangaId":1,"chapterId":101,"date":"5000"},{"id":2,"mangaId":3,"chapterId":300,"date":"6000"}],
		 "extensions":[{"id":777,"name":"MangaDex","lang":"en","itemType":0}]
		}
	""".trimIndent()

	@Test
	fun parsesLenientFieldsAndSkipsUnusableEntries() {
		val backup = MangayomiBackup.parse(sample)
		assertEquals(listOf(1L, 2L, 3L, 4L), backup.manga.map { it.id })
		val given = backup.manga.first()
		assertEquals(listOf(10L), given.categoryIds)
		assertEquals(listOf("BL", "Music"), given.genres)
		assertEquals(0, given.itemType)
		assertEquals(1, backup.manga[1].itemType) // isManga=false -> anime
		assertEquals(12, backup.chapters.first().lastPageRead)
		assertEquals(5000L, backup.history.first().date)
		assertEquals(777L, backup.extensions.single().id)
	}

	@Test
	fun readsTheZippedBackupFormat() {
		val bytes = ByteArrayOutputStream().also { out ->
			ZipOutputStream(out).use { zip ->
				zip.putNextEntry(ZipEntry("mangayomi_2026.backup"))
				zip.write(sample.toByteArray())
				zip.closeEntry()
			}
		}.toByteArray()
		assertEquals(4, MangayomiBackup.read(bytes.inputStream()).manga.size)
		assertEquals(4, MangayomiBackup.read(sample.byteInputStream()).manga.size)
	}

	@Test(expected = IOException::class)
	fun rejectsUnrelatedJson() {
		MangayomiBackup.parse("""{"name":"package.json","version":"1"}""")
	}

	@Test
	fun plansBySourceIdThenNameAndKeepsUnmatchedTitles() {
		val backup = MangayomiBackup.parse(sample)
		val sources = listOf(
			MangayomiImportPlanner.Source(777, "MangaDex", "en", 0, installed = false),
			MangayomiImportPlanner.Source(888, "AnimeSite", "all", 1, installed = true),
		)
		val plan = MangayomiImportPlanner.plan(backup, sources)
		// Favourites plus anything with history; "Never opened" isn't in the library and is left out.
		assertEquals(listOf(1L, 2L, 3L), plan.titles.map { it.id })
		assertEquals(777L, plan.matched[1]?.id)
		assertEquals(888L, plan.matched[2]?.id) // matched by name despite the language change
		assertEquals(listOf(3L), plan.unmatched.map { it.id })
		assertEquals(listOf(777L), plan.toInstall.map { it.id })
		assertEquals("MANGAYOMI_UNKNOWN_SITE", MangayomiImportPlanner.fallbackSourceName(plan.unmatched.single()))
	}

	@Test
	fun progressUsesHistoryChapterAndReadShare() {
		val backup = MangayomiBackup.parse(sample)
		val progress = MangayomiImportPlanner.progress(backup.manga[0], backup)!!
		assertEquals("/c/2", progress.chapter.url)
		assertEquals(4, progress.page)
		assertEquals(0.5f, progress.percent, 0.001f)
		assertEquals(4, progress.chapters)
		assertEquals(5000L, progress.date)
		assertNull(MangayomiImportPlanner.progress(backup.manga[3], backup))
	}

	@Test
	fun uidMatchesTheJsRepositoryScheme() {
		// Same formula as parsers' generateUid: imported titles must get the ids the source produces.
		var h = 1125899906842597L
		"js:777".forEach { h = 31 * h + it.code }
		"/manga/given".forEach { h = 31 * h + it.code }
		assertEquals(h, jsUid("js:777", "/manga/given"))
		assertFalse(jsUid("js:777", "/a") == jsUid("js:778", "/a"))
		assertTrue(jsUid("x", "") != 0L)
	}
}
