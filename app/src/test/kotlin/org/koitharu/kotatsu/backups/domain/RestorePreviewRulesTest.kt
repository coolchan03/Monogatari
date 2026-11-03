package org.koitharu.kotatsu.backups.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.backups.data.BackupCategoryMergePlan
import org.koitharu.kotatsu.backups.domain.RestorePreviewRules.Outcome

class RestorePreviewRulesTest {

	@Test
	fun missingLocalEntryIsNew() {
		assertEquals(Outcome.NEW, RestorePreviewRules.classify(null, 0L, 100L, 0L))
	}

	@Test
	fun newerBackupReplacesAndOlderOrEqualKeepsLocal() {
		assertEquals(Outcome.NEWER, RestorePreviewRules.classify(100L, 0L, 200L, 0L))
		assertEquals(Outcome.OLDER, RestorePreviewRules.classify(200L, 0L, 100L, 0L))
		assertEquals(Outcome.SAME, RestorePreviewRules.classify(200L, 0L, 200L, 0L))
	}

	@Test
	fun localDeletionBeatsOlderBackupButNotNewerOne() {
		assertEquals(Outcome.OLDER, RestorePreviewRules.classify(120L, 300L, 250L, 0L))
		assertEquals(Outcome.NEWER, RestorePreviewRules.classify(120L, 300L, 301L, 0L))
	}

	@Test
	fun backupDeletionRemovesOnlyLiveLocalEntries() {
		assertEquals(Outcome.REMOVES, RestorePreviewRules.classify(200L, 0L, 140L, 250L))
		assertEquals(Outcome.IGNORED, RestorePreviewRules.classify(null, 0L, 140L, 250L))
		assertEquals(Outcome.IGNORED, RestorePreviewRules.classify(200L, 300L, 140L, 250L))
		// An older deletion cannot remove a newer local entry.
		assertEquals(Outcome.IGNORED, RestorePreviewRules.classify(500L, 0L, 140L, 250L))
	}

	@Test
	fun countsAccumulatePerOutcome() {
		val outcomes = listOf(Outcome.NEW, Outcome.NEW, Outcome.NEWER, Outcome.OLDER, Outcome.SAME, Outcome.REMOVES, Outcome.IGNORED)
		val counts = outcomes.fold(RestorePreviewCounts()) { acc, o -> acc.plus(o) }
		assertEquals(RestorePreviewCounts(new = 2, newer = 1, same = 1, older = 1, removed = 1), counts)
		assertEquals(5, counts.total)
	}

	private fun local(id: Long, title: String, deletedAt: Long = 0L) = BackupCategoryMergePlan.LocalCategory(id, title, deletedAt)

	@Test
	fun categoriesAreMatchedByTitleAndNewOnesGetUnusedIds() {
		val plan = RestorePreviewRules.planCategories(
			incoming = listOf(
				RestorePreviewRules.IncomingCategory(1, "Reading", 0L),
				RestorePreviewRules.IncomingCategory(2, "  reading ", 0L),
				RestorePreviewRules.IncomingCategory(3, "Later", 0L),
				RestorePreviewRules.IncomingCategory(4, "Gone", 50L),
			),
			local = listOf(local(10, "Reading"), local(11, "Old", 5L)),
		)
		assertEquals(1, plan.new)
		assertEquals(2, plan.matched)
		assertEquals(10L, plan.remap[1])
		assertEquals(10L, plan.remap[2])
		assertEquals(true, plan.remap.getValue(3) < 0)
		assertEquals(false, plan.remap.containsKey(4))
	}

	@Test
	fun repeatedNewTitleIsCreatedOnce() {
		val plan = RestorePreviewRules.planCategories(
			incoming = listOf(
				RestorePreviewRules.IncomingCategory(1, "Later", 0L),
				RestorePreviewRules.IncomingCategory(2, "later", 0L),
			),
			local = emptyList(),
		)
		assertEquals(1, plan.new)
		assertEquals(1, plan.matched)
		assertEquals(plan.remap[1], plan.remap[2])
	}

	@Test
	fun settingsCountSkipsUnsafeKeys() {
		val keys = listOf("theme", "proxy_password", "app_password", "reader_mode", "backup_folder_sync")
		val safe = { key: String -> !key.contains("password") && key != "backup_folder_sync" }
		assertEquals(2, RestorePreviewRules.countSettings(keys, safe))
	}
}
