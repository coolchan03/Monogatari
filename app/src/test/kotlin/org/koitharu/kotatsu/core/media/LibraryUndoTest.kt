package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.koitharu.kotatsu.core.media.LibraryShelf.Item
import org.koitharu.kotatsu.core.media.LibraryShelf.Origin
import org.koitharu.kotatsu.core.media.LibraryUndo.Kind
import java.io.File

class LibraryUndoTest {

	@get:Rule
	val folder = TemporaryFolder()

	private fun book(name: String) = Item(
		key = "/books/$name", title = name, detail = "", kind = MediaType.BOOK, origin = Origin.IMPORTED,
		file = File("/books/$name"),
	)

	private fun media(uri: String) = Item(
		key = uri, title = uri, detail = "", kind = MediaType.VIDEO, origin = Origin.IMPORTED,
		media = LocalMediaShelf.Entry(uri = uri, name = "$uri.mp4", kind = MediaType.VIDEO.name),
	)

	@Test
	fun kindFollowsTheSamePrecedenceAsRemove() {
		assertEquals(Kind.MEDIA, LibraryUndo.kindOf(false, false, false, false, true))
		assertEquals(Kind.BOOK, LibraryUndo.kindOf(false, false, false, true, false))
		assertEquals(Kind.FAVOURITE, LibraryUndo.kindOf(true, false, true, false, false))
		// A favourite that is also downloaded is only un-favourited; the download stays.
		assertEquals(Kind.FAVOURITE, LibraryUndo.kindOf(true, true, true, false, false))
		assertEquals(Kind.DOWNLOAD, LibraryUndo.kindOf(false, true, true, false, false))
		assertEquals(Kind.NONE, LibraryUndo.kindOf(false, false, true, false, false))
	}

	@Test
	fun planSplitsAndCountsEachKeyOnce() {
		val plan = LibraryUndo.plan(listOf(book("a.epub"), media("content://v/1"), book("a.epub"), media("content://v/2")))
		assertEquals(listOf("/books/a.epub"), plan.books.map { it.key })
		assertEquals(listOf("content://v/1", "content://v/2"), plan.media.map { it.key })
		assertEquals(3, plan.undoableCount)
		assertTrue(plan.downloads.isEmpty())
		assertFalse(plan.isEmpty)
	}

	@Test
	fun emptyPlanForNothingToRemove() {
		assertTrue(LibraryUndo.plan(emptyList()).isEmpty)
	}

	@Test
	fun summaryNamesOneTitleOrCountsMany() {
		assertEquals("One", LibraryUndo.summarize(listOf("One")).title)
		val many = LibraryUndo.summarize(listOf("A", "B", "C", "D", "E"))
		assertEquals(5, many.count)
		assertNull(many.title)
		assertEquals(0, LibraryUndo.summarize(emptyList()).count)
	}

	@Test
	fun staleMeansOlderThanTheLimit() {
		assertFalse(LibraryUndo.isStale(modifiedAt = 1_000, now = 1_000 + LibraryUndo.STALE_MILLIS))
		assertTrue(LibraryUndo.isStale(modifiedAt = 1_000, now = 1_001 + LibraryUndo.STALE_MILLIS))
	}

	@Test
	fun trashedBookMovesBackToItsOriginalPlace() {
		val shelf = folder.newFolder("imported-books")
		val trash = folder.newFolder("library-trash")
		val file = File(shelf, "book-1-Story.epub").apply { writeText("content") }
		val moved = LibraryUndo.moveToTrash(trash, "1", file)
		assertNotNull(moved)
		assertFalse(file.exists())
		assertTrue(moved!!.trashed.isFile)
		assertTrue(LibraryUndo.restore(moved))
		assertEquals("content", file.readText())
		assertFalse(moved.trashed.exists())
	}

	@Test
	fun restoreRefusesToOverwriteANewerFile() {
		val shelf = folder.newFolder("imported-books")
		val trash = folder.newFolder("library-trash")
		val file = File(shelf, "b.epub").apply { writeText("old") }
		val moved = LibraryUndo.moveToTrash(trash, "1", file)!!
		file.writeText("new")
		assertFalse(LibraryUndo.restore(moved))
		assertEquals("new", file.readText())
		assertTrue(moved.trashed.exists())
	}

	@Test
	fun discardDeletesTheTrashedCopyAndItsBatchFolder() {
		val shelf = folder.newFolder("imported-books")
		val trash = folder.newFolder("library-trash")
		val moved = LibraryUndo.moveToTrash(trash, "1", File(shelf, "b.epub").apply { writeText("x") })!!
		LibraryUndo.discard(moved)
		assertFalse(moved.trashed.exists())
		assertFalse(File(trash, "1").exists())
	}

	@Test
	fun movingAMissingFileFails() {
		val trash = folder.newFolder("library-trash")
		assertNull(LibraryUndo.moveToTrash(trash, "1", File(folder.root, "nope.epub")))
	}

	@Test
	fun purgeRemovesOnlyStaleBatches() {
		val trash = folder.newFolder("library-trash")
		val old = File(trash, "old").apply { mkdirs() }
		File(old, "a.epub").writeText("x")
		val fresh = File(trash, "fresh").apply { mkdirs() }
		File(fresh, "b.epub").writeText("y")
		val now = System.currentTimeMillis()
		old.setLastModified(now - 2 * LibraryUndo.STALE_MILLIS)
		assertEquals(1, LibraryUndo.purgeStale(trash, now))
		assertFalse(old.exists())
		assertTrue(fresh.exists())
	}
}

class LibraryUndoOfflineTest {

	private fun offline(id: String) = Item(
		key = LibraryShelf.offlineKey(id), title = id, detail = "", kind = MediaType.VIDEO, origin = Origin.ON_DEVICE,
		offline = org.koitharu.kotatsu.videodl.OfflineVideoStore.Entry(
			id = id, title = id, sourceName = "s", chapterId = 1L, chapterUrl = "u", mediaType = "VIDEO", streamUrl = "https://x/$id",
		),
	)

	@Test
	fun offlineKeyIsPrefixedAndRecognised() {
		assertEquals("offline:abc", LibraryShelf.offlineKey("abc"))
		assertTrue(LibraryShelf.isOfflineKey("offline:abc"))
		assertFalse(LibraryShelf.isOfflineKey("123"))
		assertFalse(LibraryShelf.isOfflineKey("content://v/1"))
	}

	@Test
	fun readAtIsNeverNegative() {
		assertEquals(0L, LibraryShelf.offlineReadAt(-5L))
		assertEquals(42L, LibraryShelf.offlineReadAt(42L))
	}

	@Test
	fun offlineItemsAreNotUndoableAndNeedConfirmation() {
		assertEquals(Kind.OFFLINE, LibraryUndo.kindOf(offline("a")))
		val plan = LibraryUndo.plan(listOf(offline("a"), offline("b"), offline("a")))
		assertEquals(0, plan.undoableCount)
		assertEquals(listOf("offline:a", "offline:b"), plan.offline.map { it.key })
		assertEquals(2, plan.permanent.size)
		assertFalse(plan.isEmpty)
	}

	@Test
	fun mixedBatchKeepsUndoableAndPermanentApart() {
		val book = Item(
			key = "/b/a.epub", title = "a", detail = "", kind = MediaType.BOOK, origin = Origin.IMPORTED, file = File("/b/a.epub"),
		)
		val plan = LibraryUndo.plan(listOf(book, offline("a")))
		assertEquals(1, plan.undoableCount)
		assertEquals(1, plan.permanent.size)
	}

	@Test
	fun storageTotalIgnoresUnknownSizes() {
		assertEquals(30L, org.koitharu.kotatsu.storage.OfflineVideoStorage.totalBytes(listOf(10L, -1L, 20L)))
	}
}
