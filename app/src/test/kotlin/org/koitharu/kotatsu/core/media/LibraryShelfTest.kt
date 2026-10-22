package org.koitharu.kotatsu.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.core.media.LibraryShelf.Item
import org.koitharu.kotatsu.core.media.LibraryShelf.Origin
import org.koitharu.kotatsu.core.media.LibraryShelf.Sort
import java.io.File

class LibraryShelfTest {

	private fun item(
		key: String,
		title: String = key,
		kind: MediaType = MediaType.MANGA,
		origin: Origin = Origin.FAVOURITE,
		addedAt: Long = 0L,
		readAt: Long = 0L,
		detail: String = "",
	) = Item(key = key, title = title, detail = detail, kind = kind, origin = origin, addedAt = addedAt, readAt = readAt)

	@Test
	fun importedBooksAndMediaAreAlwaysListed() {
		val merged = LibraryShelf.merge(
			favourites = emptyList(),
			onDevice = emptyList(),
			imported = listOf(
				item("/books/a.epub", kind = MediaType.BOOK, origin = Origin.IMPORTED),
				item("content://video/1", kind = MediaType.VIDEO, origin = Origin.IMPORTED),
			),
		)
		assertEquals(listOf("/books/a.epub", "content://video/1"), merged.map { it.key })
		// Listed under All and under their own chip, even with no favourites at all.
		assertEquals(1, merged.count { LibraryShelf.matches(it, MediaType.BOOK, "") })
		assertEquals(2, merged.count { LibraryShelf.matches(it, null, "") })
	}

	@Test
	fun downloadedFavouriteIsOneItemMarkedOnDevice() {
		val merged = LibraryShelf.merge(
			favourites = listOf(item("42", kind = MediaType.BOOK, addedAt = 500L)),
			onDevice = listOf(item("42", origin = Origin.ON_DEVICE, addedAt = 100L), item("7", origin = Origin.ON_DEVICE)),
			imported = emptyList(),
		)
		assertEquals(listOf("42", "7"), merged.map { it.key })
		val favourite = merged.first()
		assertEquals(Origin.FAVOURITE, favourite.origin)
		assertTrue(favourite.favourite)
		assertTrue(favourite.onDevice)
		// The favourite's own kind wins over the generic "manga" of the local copy.
		assertEquals(MediaType.BOOK, favourite.kind)
		assertEquals(100L, favourite.addedAt)
		assertEquals(Origin.ON_DEVICE, merged[1].origin)
		assertFalse(merged[1].favourite)
	}

	@Test
	fun duplicateKeysKeepTheFirstEntry() {
		val merged = LibraryShelf.merge(
			favourites = listOf(item("1", title = "First"), item("1", title = "Again")),
			onDevice = emptyList(),
			imported = listOf(item("/a.pdf", origin = Origin.IMPORTED), item("/a.pdf", title = "Copy", origin = Origin.IMPORTED)),
		)
		assertEquals(listOf("First", "/a.pdf"), merged.map { it.title })
	}

	@Test
	fun kindFromSourceGroups() {
		val groups = mapOf(
			MediaType.VIDEO to setOf("ANIME"),
			MediaType.AUDIO to setOf("LIBRIVOX"),
			MediaType.BOOK to setOf("NOVELS"),
			MediaType.MANGA to setOf("MANGADEX"),
		)
		assertEquals(MediaType.VIDEO, LibraryShelf.kindOf("ANIME", groups))
		assertEquals(MediaType.AUDIO, LibraryShelf.kindOf("LIBRIVOX", groups))
		assertEquals(MediaType.BOOK, LibraryShelf.kindOf("NOVELS", groups))
		assertEquals(MediaType.MANGA, LibraryShelf.kindOf("MANGADEX", groups))
		// Removed or unknown sources still show, as manga.
		assertEquals(MediaType.MANGA, LibraryShelf.kindOf("GONE", groups))
		assertEquals(MediaType.MANGA, LibraryShelf.kindOf("ANIME", emptyMap()))
	}

	@Test
	fun sortOrders() {
		val items = listOf(
			item("a", title = "Vol 10", addedAt = 1L, readAt = 0L),
			item("b", title = "vol 2", addedAt = 3L, readAt = 5L),
			item("c", title = "Alpha", addedAt = 2L, readAt = 9L),
			item("d", title = "Zeta", addedAt = 4L, readAt = 0L),
		)
		assertEquals(listOf("c", "b", "a", "d"), LibraryShelf.sort(items, Sort.TITLE).map { it.key })
		assertEquals(listOf("d", "b", "c", "a"), LibraryShelf.sort(items, Sort.RECENTLY_ADDED).map { it.key })
		// Read items first (newest first), then never-read ones by when they were added.
		assertEquals(listOf("c", "b", "d", "a"), LibraryShelf.sort(items, Sort.RECENTLY_READ).map { it.key })
	}

	@Test
	fun readTimesComeFromHistory() {
		val items = listOf(item("1"), item("/b.epub", origin = Origin.IMPORTED))
		val updated = LibraryShelf.withReadTimes(items, mapOf("1" to 77L))
		assertEquals(77L, updated[0].readAt)
		assertEquals(0L, updated[1].readAt)
	}

	@Test
	fun sectionsFollowOriginOrderAndSkipEmpty() {
		val items = listOf(
			item("x", origin = Origin.ON_DEVICE),
			item("y", title = "B", origin = Origin.FAVOURITE),
			item("z", title = "A", origin = Origin.FAVOURITE),
		)
		val sections = LibraryShelf.sections(items, Sort.TITLE)
		assertEquals(listOf(Origin.FAVOURITE, Origin.ON_DEVICE), sections.map { it.origin })
		assertEquals(listOf("z", "y"), sections[0].items.map { it.key })
	}

	@Test
	fun filterAndCounts() {
		val items = listOf(
			item("1", title = "One Piece"),
			item("2", title = "Dune", kind = MediaType.BOOK, detail = "Local EPUB · Not started"),
			item("3", title = "Song", kind = MediaType.AUDIO),
		)
		assertTrue(LibraryShelf.matches(items[1], null, "  epub "))
		assertFalse(LibraryShelf.matches(items[1], MediaType.MANGA, ""))
		val counts = LibraryShelf.counts(items)
		assertEquals(3, counts[null])
		assertEquals(1, counts[MediaType.BOOK])
		assertEquals(0, counts[MediaType.VIDEO])
	}

	@Test
	fun continueFiltersByKindAndLimits() {
		val history = (1..20).map { item("$it", kind = if (it % 2 == 0) MediaType.BOOK else MediaType.MANGA) } + item("2")
		assertEquals(LibraryShelf.CONTINUE_LIMIT, LibraryShelf.continueItems(history, null).size)
		val books = LibraryShelf.continueItems(history, MediaType.BOOK)
		assertEquals(10, books.size)
		assertTrue(books.all { it.kind == MediaType.BOOK })
	}

	@Test
	fun coverCacheNameChangesWithTheFile() {
		val dir = createTempDir()
		try {
			val file = File(dir, "book-1-a.epub").apply { writeText("one") }
			val first = LibraryShelf.coverCacheName(file)
			assertEquals(first, LibraryShelf.coverCacheName(file))
			file.writeText("longer content")
			assertNotEquals(first, LibraryShelf.coverCacheName(file))
		} finally {
			dir.deleteRecursively()
		}
	}

	private fun createTempDir(): File = kotlin.io.path.createTempDirectory("library-shelf").toFile()
}
