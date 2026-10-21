package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.backups.domain.MangayomiImportPlanner.InstallVia
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A backup in the shape Mangayomi's current `writeMangayomiBackupZip` writes (version "2"). */
class MangayomiImportPlanTest {

	private val jsCode = "const mangayomiSources = [];\nclass DefaultExtension extends MProvider {}\n"

	private val sample = """
		{"version":"2",
		 "manga":[
		  {"id":1,"name":"Given","link":"/manga/given","imageUrl":"https://img/1.jpg","source":"MangaJs","lang":"en",
		   "itemType":0,"favorite":true,"categories":[10],"sourceId":501,"status":1,"dateAdded":1000,"customCoverImage":[1,2,3]},
		  {"id":2,"name":"Frieren","link":"/anime/2","source":"AnimeJs","lang":"en","itemType":1,"favorite":true,"categories":[20],"sourceId":502},
		  {"id":3,"name":"Lord of Mysteries","link":"/novel/3","source":"NovelJs","lang":"en","itemType":2,"favorite":true,"categories":[30],"sourceId":503},
		  {"id":4,"name":"Dart title","link":"/m/4","source":"DartSite","lang":"en","itemType":0,"favorite":true,"categories":[],"sourceId":504},
		  {"id":5,"name":"Repo title","link":"/m/5","source":"RepoOnly","lang":"en","itemType":2,"favorite":true,"categories":[30],"sourceId":505}
		 ],
		 "categories":[
		  {"id":10,"name":"Reading","forItemType":0,"pos":0},
		  {"id":20,"name":"Watching","forItemType":1,"pos":0},
		  {"id":30,"name":"Reading","forItemType":2,"pos":0},
		  {"id":40,"name":"Old anime","forManga":false,"pos":1}
		 ],
		 "chapters":[],
		 "history":[],
		 "extensions":[
		  {"id":501,"name":"MangaJs","lang":"en","itemType":0,"isAdded":true,"sourceCodeLanguage":1,"version":"0.1.0",
		   "sourceCode":${quoted(jsCode)},"sourceCodeUrl":"https://example.org/manga.js","baseUrl":"https://manga.example",
		   "repo":{"name":"r","jsonUrl":"https://example.org/index.json"}},
		  {"id":502,"name":"AnimeJs","lang":"en","itemType":1,"isAdded":true,"sourceCodeLanguage":1,"version":"0.2.0",
		   "sourceCode":"","sourceCodeUrl":"https://example.org/anime.js","repo":{"jsonUrl":"https://example.org/anime_index.json"}},
		  {"id":503,"name":"NovelJs","lang":"en","itemType":2,"isAdded":true,"sourceCodeLanguage":1,"version":"0.3.0",
		   "sourceCode":${quoted(jsCode)},"sourceCodeUrl":"https://example.org/novel.js"},
		  {"id":504,"name":"DartSite","lang":"en","itemType":0,"isAdded":true,"sourceCodeLanguage":0,"sourceCode":"class X {}"},
		  {"id":505,"name":"RepoOnly","lang":"en","itemType":2,"isAdded":true,"sourceCodeLanguage":1,"version":"1.0.0","sourceCode":${quoted(jsCode)}},
		  {"id":506,"name":"MihonExt","lang":"en","itemType":0,"isAdded":true,"sourceCodeLanguage":2,"sourceCode":"UEsDBA==",
		   "repo":{"jsonUrl":"https://mihon.example/index.min.json"}},
		  {"id":507,"name":"NotInstalled","lang":"en","itemType":0,"isAdded":false,"sourceCodeLanguage":1,"sourceCodeUrl":"https://example.org/x.js"},
		  {"id":508,"name":"Installed here","lang":"en","itemType":0,"isAdded":true,"sourceCodeLanguage":1,"sourceCode":${quoted(jsCode)}}
		 ],
		 "settings":[{"mangaExtensionsRepo":[{"jsonUrl":"https://example.org/index.json"},{"jsonUrl":"http://insecure.example/index.json"}],
		   "animeExtensionsRepo":[{"jsonUrl":"https://example.org/anime_index.json"}],
		   "novelExtensionsRepo":"https://example.org/novel_index.json"}]
		}
	""".trimIndent()

	private fun quoted(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""

	private val available = listOf(
		MangayomiImportPlanner.Source(505, "RepoOnly", "en", 2, installed = false, version = "1.0.1"),
		MangayomiImportPlanner.Source(508, "Installed here", "en", 0, installed = true, version = "1.0.0"),
	)

	@Test
	fun decodesCurrentFormat() {
		val backup = MangayomiBackup.parse(sample)
		assertEquals(listOf(0, 1, 2, 0, 2), backup.manga.map { it.itemType })
		assertEquals(listOf(0, 1, 2, 1), backup.categories.map { it.itemType })
		val manga = backup.extensions.first { it.id == 501L }
		assertTrue(manga.isAdded && manga.isJavaScript)
		assertEquals(jsCode, manga.sourceCode)
		assertEquals("https://example.org/index.json", manga.repoUrl)
		assertEquals("0.1.0", manga.version)
		assertFalse(backup.extensions.first { it.id == 504L }.isJavaScript)
		assertFalse(backup.extensions.first { it.id == 507L }.isAdded)
		assertEquals(
			listOf(
				"https://example.org/index.json",
				"http://insecure.example/index.json",
				"https://example.org/anime_index.json",
				"https://example.org/novel_index.json",
			),
			backup.settingsRepoUrls,
		)
	}

	@Test
	fun plansExtensionInstallsFromTheBackup() {
		val plan = MangayomiImportPlanner.plan(MangayomiBackup.parse(sample), available)
		val attempts = plan.extensionInstalls.associate { it.extension.id to it.attempts }
		assertEquals(listOf(501L, 502L, 503L, 505L), attempts.keys.toList())
		assertEquals(listOf(InstallVia.BACKUP_CODE, InstallVia.SOURCE_URL, InstallVia.REPOSITORY), attempts[501])
		// No saved code: the extension's own URL, then any repository.
		assertEquals(listOf(InstallVia.SOURCE_URL, InstallVia.REPOSITORY), attempts[502])
		// A repository copy at least as new as the backup's comes first, the saved code is the fallback.
		assertEquals(listOf(InstallVia.REPOSITORY, InstallVia.BACKUP_CODE), attempts[505])
		assertEquals(listOf("DartSite", "MihonExt"), plan.unsupportedExtensions.map { it.name })
		assertEquals(1, plan.alreadyInstalled)
		// Repository sources already covered by an extension install aren't installed twice.
		assertTrue(plan.toInstall.isEmpty())
	}

	@Test
	fun importsMangaAnimeAndNovelsEvenWithoutARunnableExtension() {
		val plan = MangayomiImportPlanner.plan(MangayomiBackup.parse(sample), available)
		assertEquals(listOf(1L, 2L, 3L, 4L, 5L), plan.titles.map { it.id })
		assertEquals(501L, plan.matched[1]?.id)
		assertEquals(502L, plan.matched[2]?.id)
		assertEquals(503L, plan.matched[3]?.id)
		assertEquals(505L, plan.matched[5]?.id)
		// The Dart title is still imported, under its placeholder source.
		assertEquals(listOf(4L), plan.unmatched.map { it.id })
		assertEquals("MANGAYOMI_DARTSITE", MangayomiImportPlanner.fallbackSourceName(plan.unmatched.single()))
	}

	@Test
	fun restoresOnlyHttpsRepositoriesForJavaScriptExtensions() {
		val backup = MangayomiBackup.parse(sample)
		assertEquals(
			listOf(
				"https://example.org/anime_index.json",
				"https://example.org/novel_index.json",
			),
			MangayomiImportPlanner.repoUrls(backup, knownRepos = listOf("https://example.org/index.json")),
		)
		assertFalse(MangayomiImportPlanner.repoUrls(backup).any { "mihon" in it || it.startsWith("http:") })
	}

	@Test
	fun installableCodeMatchesTheRepositoryChecks() {
		assertTrue(MangayomiImportPlanner.isInstallableCode(jsCode))
		assertFalse(MangayomiImportPlanner.isInstallableCode("console.log(1)"))
		assertFalse(MangayomiImportPlanner.isInstallableCode("class DefaultExtension {}" + "x".repeat(2 * 1024 * 1024)))
	}

	@Test
	fun sourcesOnlyBackupIsAccepted() {
		val backup = MangayomiBackup.parse(
			"""{"version":"2","extensions":[{"id":1,"name":"A","lang":"en","isAdded":true,"sourceCodeLanguage":1,"sourceCode":${quoted(jsCode)}}]}""",
		)
		assertEquals(1, MangayomiImportPlanner.plan(backup, emptyList()).extensionInstalls.size)
	}

	@Test
	fun blanksHugeStringsWhileReading() {
		val apk = "A".repeat(5 * 1024 * 1024)
		val text = """{"manga":[{"id":1,"name":"Esc \"q\" \\","link":"/m","source":"S","favorite":true}],"extensions":[{"id":2,"name":"Big","sourceCode":"$apk"}]}"""
		val scrubbed = MangayomiBackup.readScrubbed(text.byteInputStream())
		assertTrue(scrubbed.length < 1024)
		val backup = MangayomiBackup.parse(scrubbed)
		assertEquals("Esc \"q\" \\", backup.manga.single().name)
		assertEquals(null, backup.extensions.single().sourceCode)
	}

	@Test
	fun readsTheBackupDbEntryOfAStagedFile() {
		val file = File.createTempFile("mangayomi", ".backup")
		try {
			file.outputStream().use { out ->
				ZipOutputStream(out).use { zip ->
					zip.putNextEntry(ZipEntry("mangayomi_2026-10-20.backup.db"))
					zip.write(sample.toByteArray())
					zip.closeEntry()
				}
			}
			assertEquals(5, MangayomiBackup.read(file).manga.size)
		} finally {
			file.delete()
		}
	}

	@Test(expected = MangayomiBackup.EncryptedBackupException::class)
	fun reportsEncryptedBackups() {
		val bytes = ByteArrayOutputStream().also { out ->
			ZipOutputStream(out).use { zip ->
				zip.putNextEntry(ZipEntry("mangayomi.backup.db"))
				zip.write(sample.toByteArray())
				zip.closeEntry()
			}
		}.toByteArray()
		bytes[6] = (bytes[6].toInt() or 1).toByte() // general purpose flag: encrypted
		MangayomiBackup.read(bytes.inputStream())
	}
}
