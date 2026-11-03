package org.koitharu.kotatsu.sync.conflict

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.sync.conflict.FavouriteConflicts.Choice
import org.koitharu.kotatsu.sync.conflict.FavouriteConflicts.Row
import org.koitharu.kotatsu.sync.conflict.FavouriteConflicts.Snapshot

class FavouriteConflictsTest {

    private val lastSync = 1_000L
    private val categories = mapOf(1L to "Reading", 2L to "Favourites", 3L to "Dropped")

    private fun row(manga: Long, category: Long, created: Long, deleted: Long = 0L) =
        Row(manga, "Title $manga", category, created, deleted)

    private fun snaps(vararg rows: Row, titles: Map<Long, String> = categories) =
        FavouriteConflicts.snapshots(rows.toList(), titles)

    @Test fun snapshotListsLiveCategoryTitlesSortedAndDeduplicated() {
        val s = snaps(row(1, 2, 100), row(1, 1, 300), row(1, 3, 200, deleted = 400))
        val snapshot = s.getValue(1)
        assertEquals(listOf("Favourites", "Reading"), snapshot.categories)
        assertEquals(400L, snapshot.modifiedAt) // the removal is a change too
    }

    @Test fun rowsInUnknownCategoriesAreNotMemberships() {
        val snapshot = snaps(row(1, 99, 100)).getValue(1)
        assertTrue(snapshot.categories.isEmpty())
    }

    @Test fun sameCategoriesByTitleIgnoringCaseAndIdsAreNoConflict() {
        val local = snaps(row(1, 1, 1_500), row(1, 2, 1_500))
        val other = FavouriteConflicts.snapshots(
            listOf(row(1, 20, 1_900), row(1, 10, 1_900)),
            mapOf(10L to " reading ", 20L to "FAVOURITES"),
        )
        assertTrue(FavouriteConflicts.find(local, other.values, lastSync).isEmpty())
    }

    @Test fun onlyTitlesChangedOnBothSidesAreConflicts() {
        val local = snaps(
            row(1, 1, 1_500), // changed here
            row(2, 1, 500), // unchanged here
            row(3, 1, 1_700),
        )
        val incoming = snaps(
            row(1, 3, 1_200), // changed there too -> conflict
            row(2, 3, 1_300), // only there -> merges normally
            row(3, 1, 1_900), // same membership -> nothing to ask
            row(4, 1, 1_900), // new here -> merges normally
        )
        val conflicts = FavouriteConflicts.find(local, incoming.values, lastSync)
        assertEquals(listOf(1L), conflicts.map { it.mangaId })
        assertEquals(Choice.KEEP_LOCAL, conflicts.single().recommended) // 1500 is newer than 1200
        assertEquals(listOf("Reading"), conflicts.single().local.categories)
        assertEquals(listOf("Dropped"), conflicts.single().incoming.categories)
    }

    @Test fun removalOnOneSideAndMoveOnTheOtherIsAConflict() {
        val local = snaps(row(1, 1, 500, deleted = 1_600)) // removed from Reading here
        val incoming = snaps(row(1, 3, 1_700)) // added to Dropped there
        val conflict = FavouriteConflicts.find(local, incoming.values, lastSync).single()
        assertTrue(conflict.local.categories.isEmpty())
        assertEquals(Choice.TAKE_INCOMING, conflict.recommended)
    }

    @Test fun titleWithoutRowsHereIsNotAConflict() {
        val incoming = snaps(row(5, 1, 1_900))
        assertTrue(FavouriteConflicts.find(emptyMap(), incoming.values, lastSync).isEmpty())
    }

    @Test fun firstSyncAsksAboutEveryDifference() {
        val local = snaps(row(1, 1, 500))
        val incoming = snaps(row(1, 3, 100))
        val conflict = FavouriteConflicts.find(local, incoming.values, lastSyncAt = 0L).single()
        assertEquals(Choice.KEEP_LOCAL, conflict.recommended)
    }

    @Test fun neitherChangedSinceLastSyncButDifferingStillAsks() {
        val local = snaps(row(1, 1, 100))
        val incoming = snaps(row(1, 3, 200))
        assertEquals(1, FavouriteConflicts.find(local, incoming.values, lastSync).size)
    }

    @Test fun conflictsAreSortedMostRecentFirst() {
        val local = snaps(row(1, 1, 1_100), row(2, 1, 1_800))
        val incoming = snaps(row(1, 3, 1_200), row(2, 3, 1_900))
        assertEquals(listOf(2L, 1L), FavouriteConflicts.find(local, incoming.values, lastSync).map { it.mangaId })
    }

    @Test fun resolveSplitsChoicesAndFallsBackToTheRecommendation() {
        val local = snaps(row(1, 1, 1_500), row(2, 1, 1_500), row(3, 1, 1_500), row(4, 1, 1_100))
        val incoming = snaps(row(1, 3, 1_200), row(2, 3, 1_200), row(3, 3, 1_200), row(4, 3, 1_900))
        val conflicts = FavouriteConflicts.find(local, incoming.values, lastSync)
        val resolution = FavouriteConflicts.resolve(
            conflicts,
            mapOf(1L to Choice.KEEP_LOCAL, 2L to Choice.TAKE_INCOMING, 3L to Choice.KEEP_BOTH),
        )
        assertEquals(setOf(1L), resolution.keepLocal)
        assertEquals(setOf(2L, 4L), resolution.takeIncoming) // 4 has no choice: newest (incoming) wins
        assertEquals(setOf(3L), resolution.union)
    }

    @Test fun bulkChoicesCoverEveryConflict() {
        val local = snaps(row(1, 1, 1_500), row(2, 1, 1_100))
        val incoming = snaps(row(1, 3, 1_200), row(2, 3, 1_900))
        val conflicts = FavouriteConflicts.find(local, incoming.values, lastSync)
        assertEquals(setOf(1L, 2L), FavouriteConflicts.keepLocalForAll(conflicts).keys)
        assertTrue(FavouriteConflicts.keepLocalForAll(conflicts).values.all { it == Choice.KEEP_LOCAL })
        assertTrue(FavouriteConflicts.keepBothForAll(conflicts).values.all { it == Choice.KEEP_BOTH })
        assertEquals(Choice.KEEP_LOCAL, FavouriteConflicts.newestForAll(conflicts).getValue(1))
        assertEquals(Choice.TAKE_INCOMING, FavouriteConflicts.newestForAll(conflicts).getValue(2))
        assertEquals(FavouriteConflicts.Resolution.NONE, FavouriteConflicts.resolve(emptyList(), emptyMap()))
    }

    @Test fun unionOnlyAddsLiveIncomingRows() {
        assertTrue(FavouriteConflicts.unionAddsRow(existingDeletedAt = null, incomingDeletedAt = 0L)) // new category
        assertTrue(FavouriteConflicts.unionAddsRow(existingDeletedAt = 500L, incomingDeletedAt = 0L)) // revive
        assertFalse(FavouriteConflicts.unionAddsRow(existingDeletedAt = 0L, incomingDeletedAt = 0L)) // already there
        assertFalse(FavouriteConflicts.unionAddsRow(existingDeletedAt = 0L, incomingDeletedAt = 900L)) // their removal
        assertFalse(FavouriteConflicts.unionAddsRow(existingDeletedAt = null, incomingDeletedAt = 900L))
    }

    @Test fun snapshotEqualityIsStructural() {
        assertEquals(Snapshot(1, "T", listOf("A"), 5), Snapshot(1, "T", listOf("A"), 5))
    }
}
