package org.koitharu.kotatsu.backups.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.backups.data.BackupCategoryMergePlan.Action
import org.koitharu.kotatsu.backups.data.BackupCategoryMergePlan.LocalCategory

class BackupCategoryMergePlanTest {

    private val local = listOf(
        LocalCategory(2, "Manga", 0L),
        LocalCategory(5, "Old", 100L),
    )

    @Test fun sameIdDifferentTitleIsANewCategoryNotAnOverwrite() {
        // Incoming id 2 is "Books" there, but local id 2 is "Manga".
        assertTrue(BackupCategoryMergePlan.resolve("Books", 0L, local) is Action.Insert)
    }

    @Test fun sameTitleReusesTheLocalCategoryIgnoringCaseAndPadding() {
        val action = BackupCategoryMergePlan.resolve("  manga ", 0L, local)
        assertEquals(2L, (action as Action.Reuse).localId)
    }

    @Test fun deletedLocalCategoryIsNotReusedOrRevived() {
        assertTrue(BackupCategoryMergePlan.resolve("Old", 0L, local) is Action.Insert)
    }

    @Test fun incomingDeletionOfUnknownCategoryIsSkipped() {
        assertTrue(BackupCategoryMergePlan.resolve("Gone", 50L, local) is Action.Skip)
    }

    @Test fun favouritesAreRemappedAndUnresolvedOnesSkipped() {
        val map = mapOf(2L to 9L)
        assertEquals(9L, BackupCategoryMergePlan.remap(map, 2L))
        assertNull(BackupCategoryMergePlan.remap(map, 7L))
        // No categories section in the archive: the incoming id is used as is.
        assertEquals(7L, BackupCategoryMergePlan.remap(null, 7L))
    }

    @Test fun repeatedImportMapsToTheCategoryItCreated() {
        val afterFirst = local + LocalCategory(9, "Books", 0L)
        assertEquals(9L, (BackupCategoryMergePlan.resolve("Books", 0L, afterFirst) as Action.Reuse).localId)
    }
}
