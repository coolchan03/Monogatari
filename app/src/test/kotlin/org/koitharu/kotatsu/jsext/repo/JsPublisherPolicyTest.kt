package org.koitharu.kotatsu.jsext.repo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.jsext.JsItemType
import java.nio.file.Files

class JsPublisherPolicyTest {

	private val original = "https://raw.githubusercontent.com/alice/extensions/main/index.json"
	private val impostor = "https://raw.githubusercontent.com/mallory/extensions/main/index.json"

	private fun entry(
		version: String,
		repo: String,
		id: Long = 77,
		name: String = "Example",
		baseUrl: String = "https://example.org",
		sha: String? = null,
		language: SourceCodeLanguage = SourceCodeLanguage.JAVASCRIPT,
	) = JsSourceEntry(
		id = id, name = name, baseUrl = baseUrl, lang = "en", version = version,
		itemType = JsItemType.fromCode(null), language = language,
		sourceCodeUrl = "https://example.org/source.js", iconUrl = "", apiUrl = "",
		dateFormat = "", dateFormatLocale = "", isNsfw = false, hasCloudflare = false,
		additionalParams = "", notes = "", repoUrl = repo, sha256 = sha,
	)

	@Test
	fun branchAndIndexNameDoNotChangeGithubPublisher() {
		val a = JsPublisherPolicy.publisherKey("https://raw.githubusercontent.com/Alice/Extensions/main/index.json")
		val b = JsPublisherPolicy.publisherKey("https://raw.githubusercontent.com/alice/extensions/refs/heads/master/index.min.json")
		assertEquals(a, b)
		assertEquals("raw.githubusercontent.com/alice/extensions", a)
	}

	@Test
	fun differentOwnerIsDifferentPublisher() {
		assertFalse(JsPublisherPolicy.isSamePublisher(original, impostor))
	}

	@Test
	fun lookalikeHostnamesAreNotTheSamePublisher() {
		val installed = "https://extensions.example.org/repo/index.json"
		assertFalse(JsPublisherPolicy.isSamePublisher(installed, "https://extensions.example.org.evil.test/repo/index.json"))
		assertFalse(JsPublisherPolicy.isSamePublisher(installed, "https://evil.test/extensions.example.org/repo/index.json"))
		assertFalse(JsPublisherPolicy.isSamePublisher(installed, "https://extensions.example.org@evil.test/repo/index.json"))
		assertTrue(JsPublisherPolicy.isSamePublisher(installed, "https://EXTENSIONS.example.org/repo/index.json"))
	}

	@Test
	fun blankInstalledRepoIsTreatedAsLegacyUnpinned() {
		assertTrue(JsPublisherPolicy.isSamePublisher("", impostor))
		assertFalse(JsPublisherPolicy.isSamePublisher(original, ""))
	}

	@Test
	fun higherVersionFromAnotherPublisherDoesNotDisplaceInstalledPublisher() {
		val installed = entry("1.0", original)
		val group = listOf(entry("1.0", original), entry("99.0", impostor))
		val selection = JsPublisherPolicy.select(group, installed)
		assertEquals("1.0", selection.entry?.version)
		assertEquals(original, selection.entry?.repoUrl)
		assertEquals(listOf(impostor), selection.foreign.map { it.repoUrl })
	}

	@Test
	fun ownPublisherUpdateIsStillOffered() {
		val selection = JsPublisherPolicy.select(listOf(entry("2.0", original), entry("99.0", impostor)), entry("1.0", original))
		assertEquals("2.0", selection.entry?.version)
	}

	@Test
	fun onlyForeignPublisherLeavesNoSilentCandidate() {
		val selection = JsPublisherPolicy.select(listOf(entry("5.0", impostor)), entry("1.0", original))
		assertNull(selection.entry)
		assertEquals(1, selection.foreign.size)
	}

	@Test
	fun notInstalledStillPicksNewestRunnable() {
		val dart = entry("100.0", impostor, language = SourceCodeLanguage.DART)
		val selection = JsPublisherPolicy.select(listOf(entry("1.0", original), entry("2.0", impostor), dart), null)
		assertEquals("2.0", selection.entry?.version)
		assertTrue(selection.foreign.isEmpty())
	}

	@Test
	fun conflictDetection() {
		// Same id, same version, different declared hashes: impersonation signature.
		val a = entry("1.0", original, sha = "a".repeat(64))
		val b = entry("1.0", impostor, sha = "b".repeat(64))
		assertTrue(JsPublisherPolicy.hasSuspiciousConflict(listOf(a, b)))
		// Same id pointing at a different website or name.
		assertTrue(JsPublisherPolicy.hasSuspiciousConflict(listOf(entry("1.0", original), entry("1.0", impostor, baseUrl = "https://evil.test"))))
		assertTrue(JsPublisherPolicy.hasSuspiciousConflict(listOf(entry("1.0", original), entry("1.0", impostor, name = "Other"))))
		// Plain mirror of the same extension is not a conflict.
		assertFalse(JsPublisherPolicy.hasSuspiciousConflict(listOf(entry("1.0", original), entry("1.1", impostor))))
		assertFalse(JsPublisherPolicy.hasSuspiciousConflict(listOf(entry("1.0", original), entry("1.1", original))))
	}

	// --- Store-level enforcement (covers update worker, repair, bootstrap, backup restore, importer) ---

	@Test
	fun storeRefusesCrossPublisherInstallAndKeepsOriginalCode() {
		val dir = Files.createTempDirectory("js-publisher").toFile()
		try {
			val store = JsSourceStore(dir)
			store.installValidated(entry("1.0", original), "class DefaultExtension { good }")
			val error = assertThrows(PublisherChangeException::class.java) {
				store.installValidated(entry("99.0", impostor), "class DefaultExtension { evil }")
			}
			assertEquals(77L, error.sourceId)
			val installed = store.get(77)!!
			assertEquals("1.0", installed.entry.version)
			assertEquals(original, installed.entry.repoUrl)
			assertTrue(installed.code.contains("good"))
		} finally {
			dir.deleteRecursively()
		}
	}

	@Test
	fun approvedTransferSwitchesPublisherAndLaterUpdatesFollowTheNewOne() {
		val dir = Files.createTempDirectory("js-publisher").toFile()
		try {
			val store = JsSourceStore(dir)
			store.installValidated(entry("1.0", original), "class DefaultExtension { good }")
			store.installValidated(entry("2.0", impostor), "class DefaultExtension { moved }", approvePublisherChange = true)
			assertEquals(impostor, store.get(77)!!.entry.repoUrl)
			// The old publisher is now the foreign one.
			assertThrows(PublisherChangeException::class.java) {
				store.installValidated(entry("3.0", original), "class DefaultExtension { back }")
			}
		} finally {
			dir.deleteRecursively()
		}
	}

	@Test
	fun approvalDoesNotBypassDowngradeProtection() {
		val dir = Files.createTempDirectory("js-publisher").toFile()
		try {
			val store = JsSourceStore(dir)
			store.installValidated(entry("5.0", original), "class DefaultExtension { good }")
			assertThrows(java.io.IOException::class.java) {
				store.installValidated(entry("1.0", impostor), "class DefaultExtension { old }", approvePublisherChange = true)
			}
			assertNotEquals(impostor, store.get(77)!!.entry.repoUrl)
		} finally {
			dir.deleteRecursively()
		}
	}

	@Test
	fun samePublisherUpdatesAndLegacyBlankInstallsStillWork() {
		val dir = Files.createTempDirectory("js-publisher").toFile()
		try {
			val store = JsSourceStore(dir)
			store.installValidated(entry("1.0", original), "class DefaultExtension { a }")
			store.installValidated(
				entry("2.0", "https://raw.githubusercontent.com/alice/extensions/master/index.min.json"),
				"class DefaultExtension { b }",
			)
			assertEquals("2.0", store.get(77)!!.entry.version)
			// Legacy install with no recorded provenance adopts the next updater.
			store.installValidated(entry("1.0", "", id = 78), "class DefaultExtension { legacy }")
			store.installValidated(entry("2.0", impostor, id = 78), "class DefaultExtension { legacy2 }")
			assertEquals(impostor, store.get(78)!!.entry.repoUrl)
		} finally {
			dir.deleteRecursively()
		}
	}
}
