package org.koitharu.kotatsu.core.media

import java.io.File

/**
 * Pure rules for multi-select in the Library: toggling and select-all over the visible list, pruning the selection
 * when the list changes, and applying one collection choice to many items. Unit-tested.
 */
internal object LibrarySelection {

	fun toggle(selected: Set<String>, key: String): Set<String> =
		if (key in selected) selected - key else selected + key

	/** True when [visible] is not empty and every visible key is selected. */
	fun allSelected(selected: Set<String>, visible: Collection<String>): Boolean =
		visible.isNotEmpty() && visible.all { it in selected }

	/** Selects everything visible; when everything visible is already selected, deselects it instead. */
	fun toggleAll(selected: Set<String>, visible: Collection<String>): Set<String> =
		if (allSelected(selected, visible)) selected - visible.toSet() else selected + visible

	/** Drops selected keys that are no longer in the library (removed, or deleted elsewhere). */
	fun prune(selected: Set<String>, existing: Collection<String>): Set<String> {
		if (selected.isEmpty()) return selected
		val known = existing.toHashSet()
		return selected.filterTo(LinkedHashSet()) { it in known }
	}

	/**
	 * Applies the collection dialog to several items. A collection the user did not touch keeps its members as they
	 * were (items can be in some of them and not in others); a touched one gets all [keys] added or removed.
	 */
	fun applyCollections(
		data: Map<String, Set<String>>,
		keys: Collection<String>,
		names: List<String>,
		checked: List<Boolean>,
		touched: Set<Int>,
	): Map<String, Set<String>> {
		var result = data
		names.forEachIndexed { i, name ->
			if (i !in touched) return@forEachIndexed
			for (key in keys) {
				result = if (checked[i]) LibraryCollections.add(result, name, key) else LibraryCollections.remove(result, name, key)
			}
		}
		return result
	}

	/** Initial tick of a collection in the dialog: only when every selected item is already in it. */
	fun isChecked(data: Map<String, Set<String>>, name: String, keys: Collection<String>): Boolean {
		val members = data[name].orEmpty()
		return keys.isNotEmpty() && keys.all { it in members }
	}
}

/**
 * Pure rules for removing from the Library with Undo: which way an item is removed, how a batch is split into
 * undoable and permanent parts, the snackbar summary, and the book "trash" folder in the cache. Unit-tested.
 */
internal object LibraryUndo {

	/** How long the Undo snackbar stays up. */
	const val UNDO_MILLIS = 8000

	/** Cache subfolder that holds removed books until the removal is final. */
	const val TRASH_DIR = "library-trash"

	/** Trash older than this is leftovers of an earlier run and is purged when the Library opens. */
	const val STALE_MILLIS = 10L * 60L * 1000L

	enum class Kind {
		/** Un-favourite; can be undone. */
		FAVOURITE,

		/** A downloaded / imported comic: deleted from the device, cannot be undone. */
		DOWNLOAD,

		/** An imported book file: moved to the trash, can be undone. */
		BOOK,

		/** The user's own video or audio: only forgotten, can be undone. */
		MEDIA,

		NONE,
	}

	/** Same precedence as the Library's remove: media, then book file, then favourite, then download. */
	fun kindOf(favourite: Boolean, onDevice: Boolean, hasManga: Boolean, hasFile: Boolean, hasMedia: Boolean): Kind = when {
		hasMedia -> Kind.MEDIA
		hasFile -> Kind.BOOK
		hasManga && favourite -> Kind.FAVOURITE
		hasManga && onDevice -> Kind.DOWNLOAD
		else -> Kind.NONE
	}

	fun kindOf(item: LibraryShelf.Item): Kind = kindOf(
		favourite = item.favourite,
		onDevice = item.onDevice,
		hasManga = item.manga != null,
		hasFile = item.file != null,
		hasMedia = item.media != null,
	)

	/** A batch of items split by how each is removed. Items with the same key are counted once. */
	class Plan(
		val favourites: List<LibraryShelf.Item>,
		val books: List<LibraryShelf.Item>,
		val media: List<LibraryShelf.Item>,
		/** Need the "cannot be undone" confirmation. */
		val downloads: List<LibraryShelf.Item>,
	) {
		val undoableCount: Int get() = favourites.size + books.size + media.size
		val isEmpty: Boolean get() = undoableCount == 0 && downloads.isEmpty()
	}

	fun plan(items: List<LibraryShelf.Item>): Plan {
		val favourites = ArrayList<LibraryShelf.Item>()
		val books = ArrayList<LibraryShelf.Item>()
		val media = ArrayList<LibraryShelf.Item>()
		val downloads = ArrayList<LibraryShelf.Item>()
		for (item in items.distinctBy { it.key }) {
			when (kindOf(item)) {
				Kind.FAVOURITE -> favourites += item
				Kind.BOOK -> books += item
				Kind.MEDIA -> media += item
				Kind.DOWNLOAD -> downloads += item
				Kind.NONE -> Unit
			}
		}
		return Plan(favourites, books, media, downloads)
	}

	/** What the snackbar says: one title, or a count when several items were removed. */
	class Summary(val count: Int, val title: String?)

	fun summarize(titles: List<String>): Summary = Summary(titles.size, titles.singleOrNull())

	fun isStale(modifiedAt: Long, now: Long, maxAge: Long = STALE_MILLIS): Boolean = now - modifiedAt > maxAge

	/** A removed book: where it was and where it waits until the removal is final. */
	class TrashedBook(val original: File, val trashed: File)

	/** Moves [file] to `root/batch/name` (cache and files are normally on one volume, so this is a rename). */
	fun moveToTrash(root: File, batch: String, file: File): TrashedBook? {
		val target = File(File(root, batch), file.name)
		return if (move(file, target)) TrashedBook(file, target) else null
	}

	/** Moves the book back to where it was; false when something else is there now or the move fails. */
	fun restore(book: TrashedBook): Boolean {
		if (book.original.exists() || !book.trashed.exists()) return false
		val ok = move(book.trashed, book.original)
		if (ok) book.trashed.parentFile?.delete()
		return ok
	}

	/** Deletes the trashed copy for good. */
	fun discard(book: TrashedBook) {
		runCatching { book.trashed.delete() }
		runCatching { book.trashed.parentFile?.delete() }
	}

	/** Deletes trash batches older than [maxAge]; returns how many were purged. Call off the main thread. */
	fun purgeStale(root: File, now: Long, maxAge: Long = STALE_MILLIS): Int {
		var purged = 0
		for (entry in root.listFiles().orEmpty()) {
			if (isStale(entry.lastModified(), now, maxAge) && entry.deleteRecursively()) purged++
		}
		return purged
	}

	private fun move(from: File, to: File): Boolean {
		to.parentFile?.mkdirs()
		if (from.renameTo(to)) return true
		return try {
			from.copyTo(to, overwrite = false)
			from.delete()
			true
		} catch (e: Exception) {
			runCatching { to.delete() }
			false
		}
	}
}
