package org.koitharu.kotatsu.core.media

import org.koitharu.kotatsu.core.util.AlphanumComparator
import org.koitharu.kotatsu.parsers.model.Manga
import java.io.File
import java.util.Locale

/**
 * Pure rules for the unified Library: which items it lists, how favourites, downloads and the user's own files
 * merge into one list, and how that list is filtered, counted, sorted and grouped. Unit-tested.
 */
internal object LibraryShelf {

	/** Where an item comes from. The declaration order is the order of the sections on screen. */
	enum class Origin { FAVOURITE, IMPORTED, ON_DEVICE }

	enum class Sort { RECENTLY_ADDED, RECENTLY_READ, TITLE }

	data class Item(
		/** Collection key; must stay manga id / media uri / file path, which saved collections already use. */
		val key: String,
		val title: String,
		val detail: String,
		val kind: MediaType,
		val origin: Origin,
		val addedAt: Long = 0L,
		val readAt: Long = 0L,
		val favourite: Boolean = origin == Origin.FAVOURITE,
		val onDevice: Boolean = origin == Origin.ON_DEVICE,
		val manga: Manga? = null,
		/** An imported book in Monogatari's own storage. */
		val file: File? = null,
		/** The user's own video or audio file; [missing] when it can no longer be opened. */
		val media: LocalMediaShelf.Entry? = null,
		val missing: Boolean = false,
		/** Local cover image (an EPUB's own cover, extracted once) for items without a [Manga.coverUrl]. */
		val coverFile: File? = null,
	)

	/** What a section is called on screen. Imported files and downloads share one "On this device" group. */
	enum class Group { FAVOURITES, ON_THIS_DEVICE }

	data class Section(val group: Group, val items: List<Item>)

	fun groupOf(item: Item): Group = when (item.origin) {
		Origin.FAVOURITE -> Group.FAVOURITES
		Origin.IMPORTED, Origin.ON_DEVICE -> Group.ON_THIS_DEVICE
	}

	/** True for anything stored on this device: imported files and downloads, also when they are favourites too. */
	fun isOnThisDevice(item: Item): Boolean = item.onDevice || item.origin == Origin.IMPORTED

	fun mangaKey(id: Long): String = id.toString()

	/** Media kind of a saved title from its persisted source name; unknown and older sources count as manga. */
	fun kindOf(sourceName: String, groups: Map<MediaType, Set<String>>): MediaType = when {
		sourceName in groups[MediaType.VIDEO].orEmpty() -> MediaType.VIDEO
		sourceName in groups[MediaType.AUDIO].orEmpty() -> MediaType.AUDIO
		sourceName in groups[MediaType.BOOK].orEmpty() -> MediaType.BOOK
		else -> MediaType.MANGA
	}

	/**
	 * One list with every item the user added. A favourite that is also downloaded stays a single favourite
	 * marked [Item.onDevice] (keeping the earliest added time); any other duplicate key keeps its first entry.
	 */
	fun merge(favourites: List<Item>, onDevice: List<Item>, imported: List<Item>): List<Item> {
		val result = LinkedHashMap<String, Item>()
		for (item in favourites) result.putIfAbsent(item.key, item)
		for (item in onDevice) {
			val existing = result[item.key]
			if (existing == null) {
				result[item.key] = item
			} else if (!existing.onDevice) {
				result[item.key] = existing.copy(
					onDevice = true,
					addedAt = earliest(existing.addedAt, item.addedAt),
				)
			}
		}
		for (item in imported) result.putIfAbsent(item.key, item)
		return result.values.toList()
	}

	/** Copies the last-read time from history (manga id key -> time) onto matching items. */
	fun withReadTimes(items: List<Item>, readAt: Map<String, Long>): List<Item> =
		if (readAt.isEmpty()) items else items.map { item ->
			val time = readAt[item.key] ?: return@map item
			if (time > item.readAt) item.copy(readAt = time) else item
		}

	fun matches(item: Item, kind: MediaType?, query: String): Boolean {
		if (kind != null && item.kind != kind) return false
		val q = query.trim()
		return q.isEmpty() || item.title.contains(q, ignoreCase = true) || item.detail.contains(q, ignoreCase = true)
	}

	/** Item count per chip: null is All; every kind is present, so empty chips show 0. */
	fun counts(items: List<Item>): Map<MediaType?, Int> {
		val result = LinkedHashMap<MediaType?, Int>()
		result[null] = items.size
		for (type in MediaType.entries) result[type] = items.count { it.kind == type }
		return result
	}

	private val titleComparator: Comparator<Item> = Comparator { a, b ->
		val byTitle = ALPHANUM.compare(a.title.lowercase(Locale.ROOT), b.title.lowercase(Locale.ROOT))
		if (byTitle != 0) byTitle else a.key.compareTo(b.key)
	}

	fun comparator(sort: Sort): Comparator<Item> = when (sort) {
		Sort.TITLE -> titleComparator
		Sort.RECENTLY_ADDED -> compareByDescending<Item> { it.addedAt }.then(titleComparator)
		// Never-read items follow the read ones, newest additions first.
		Sort.RECENTLY_READ -> compareByDescending<Item> { it.readAt }
			.thenByDescending { it.addedAt }
			.then(titleComparator)
	}

	fun sort(items: List<Item>, sort: Sort): List<Item> = items.sortedWith(comparator(sort))

	/** Non-empty sections in [Group] order (Favourites, On this device), each sorted by [sort]. */
	fun sections(items: List<Item>, sort: Sort): List<Section> {
		val grouped = items.groupBy(::groupOf)
		return Group.entries.mapNotNull { group ->
			grouped[group]?.takeIf { it.isNotEmpty() }?.let { Section(group, sort(it, sort)) }
		}
	}

	/**
	 * Titles for the Continue strip, filtered by the selected chip: the opened [history] (newest first) plus
	 * [resumable] items that have a saved position (imported books, audio, video), most recent first.
	 */
	fun continueItems(
		history: List<Item>,
		kind: MediaType?,
		limit: Int = CONTINUE_LIMIT,
		resumable: List<Item> = emptyList(),
	): List<Item> =
		(if (resumable.isEmpty()) history else (history + resumable).sortedWith(
			compareByDescending<Item> { it.readAt }.thenByDescending { it.addedAt },
		)).asSequence()
			.filter { kind == null || it.kind == kind }
			.distinctBy { it.key }
			.take(limit)
			.toList()

	/**
	 * The single item the Library's Continue button resumes: the most recently read of the opened [history] and the
	 * [resumable] items (imported books, audio, video) of [kind] (null is every media type). Items without a read time
	 * (started books) lose to dated ones and then go by the newest [Item.addedAt]; items whose file is [Item.missing]
	 * are skipped. On a full tie the history entry comes first.
	 */
	fun mostRecentResumable(
		history: List<Item>,
		resumable: List<Item>,
		kind: MediaType? = null,
	): Item? = (history + resumable)
		.filter { (kind == null || it.kind == kind) && !it.missing }
		.sortedWith(compareByDescending<Item> { it.readAt }.thenByDescending { it.addedAt })
		.firstOrNull()

	/** Stable cache name for an imported book's cover; changes whenever the file is replaced. */
	fun coverCacheName(file: File): String =
		"cover-" + Integer.toHexString(file.absolutePath.hashCode()) + "-" + file.length() + "-" + file.lastModified()

	const val CONTINUE_LIMIT = 12

	private val ALPHANUM = AlphanumComparator()

	private fun earliest(a: Long, b: Long): Long = when {
		a <= 0L -> b
		b <= 0L -> a
		else -> minOf(a, b)
	}
}
