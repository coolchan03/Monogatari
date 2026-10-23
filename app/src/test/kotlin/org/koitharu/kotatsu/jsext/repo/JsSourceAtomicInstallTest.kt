package org.koitharu.kotatsu.jsext.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.koitharu.kotatsu.jsext.JsItemType
import java.io.IOException
import java.nio.file.Files

class JsSourceAtomicInstallTest {
	private fun entry(version: String) = JsSourceEntry(
		id = 321, name = "Example", baseUrl = "https://example.org",
		lang = "en", version = version, itemType = JsItemType.fromCode(null),
		language = SourceCodeLanguage.JAVASCRIPT, sourceCodeUrl = "https://example.org/source.js",
		iconUrl = "", apiUrl = "", dateFormat = "", dateFormatLocale = "",
		isNsfw = false, hasCloudflare = false, additionalParams = "", notes = "",
		repoUrl = "https://example.org/index.json",
	)

	@Test
	fun staleOlderInstallIsRejectedAfterNewerCommitted() {
		val dir = Files.createTempDirectory("js-atomic").toFile()
		try {
			val store = JsSourceStore(dir)
			store.installValidated(entry("3.0"), "class DefaultExtension { v = 3 }")
			assertThrows(IOException::class.java) {
				store.installValidated(entry("2.0"), "class DefaultExtension { v = 2 }")
			}
			assertEquals("3.0", store.get(321)?.entry?.version)
		} finally {
			dir.deleteRecursively()
		}
	}

	@Test
	fun concurrentInstallsNeverEndOnTheOlderVersion() {
		val dir = Files.createTempDirectory("js-atomic").toFile()
		try {
			val store = JsSourceStore(dir)
			repeat(20) { round ->
				val v2 = Thread { runCatching { store.installValidated(entry("2.$round"), "class DefaultExtension { v = 2.$round }") } }
				val v3 = Thread { runCatching { store.installValidated(entry("3.$round"), "class DefaultExtension { v = 3.$round }") } }
				v3.start(); v2.start(); v2.join(); v3.join()
				assertEquals("3.$round", store.get(321)?.entry?.version)
			}
		} finally {
			dir.deleteRecursively()
		}
	}

	@Test
	fun sameVersionDifferentContentIsRejected() {
		val dir = Files.createTempDirectory("js-atomic").toFile()
		try {
			val store = JsSourceStore(dir)
			store.installValidated(entry("1.0"), "class DefaultExtension { a }")
			assertThrows(IOException::class.java) {
				store.installValidated(entry("1.0"), "class DefaultExtension { b }")
			}
		} finally {
			dir.deleteRecursively()
		}
	}
}
