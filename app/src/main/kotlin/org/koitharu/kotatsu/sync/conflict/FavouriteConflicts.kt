package org.koitharu.kotatsu.sync.conflict

/**
 * Favourites / category conflicts between this device and a received library, built on [SyncConflictPlanner].
 * A title is a conflict only when both devices have favourite rows for it, their category membership differs and both
 * sides changed it since the last sync (or neither did, yet they differ). Categories are compared by title because
 * category ids are generated per device.
 */
object FavouriteConflicts {

	/** One favourites row as stored in the database or in a received archive. */
	data class Row(
		val mangaId: Long,
		val title: String,
		val categoryId: Long,
		val createdAt: Long,
		val deletedAt: Long,
	)

	/** A title's membership on one device: the live category titles, and when any of its rows last changed. */
	data class Snapshot(
		val mangaId: Long,
		val title: String,
		val categories: List<String>,
		val modifiedAt: Long,
	)

	enum class Choice { KEEP_LOCAL, TAKE_INCOMING, KEEP_BOTH }

	data class Conflict(
		val mangaId: Long,
		val title: String,
		val local: Snapshot,
		val incoming: Snapshot,
		/** The planner's "newest wins" pick, selected by default. */
		val recommended: Choice,
	)

	/**
	 * Manga ids whose local categories must survive (incoming rows ignored), ids whose incoming categories replace
	 * the local ones, and ids that end up in the union of both.
	 */
	data class Resolution(val keepLocal: Set<Long>, val takeIncoming: Set<Long>, val union: Set<Long>) {

		companion object {
			val NONE = Resolution(emptySet(), emptySet(), emptySet())
		}
	}

	private fun key(title: String) = title.trim().lowercase()

	/**
	 * Groups [rows] per title. [categoryTitles] holds the live categories (id to title); rows in a category that is
	 * missing from it, or deleted rows, are not memberships but still count as a change (a removal).
	 */
	fun snapshots(rows: Collection<Row>, categoryTitles: Map<Long, String>): Map<Long, Snapshot> =
		rows.groupBy { it.mangaId }.mapValues { (id, group) ->
			val live = group.filter { it.deletedAt == 0L }.mapNotNull { categoryTitles[it.categoryId]?.trim() }
				.filter { it.isNotEmpty() }.distinctBy { key(it) }.sortedBy { key(it) }
			Snapshot(id, group.first().title, live, group.maxOf { maxOf(it.createdAt, it.deletedAt) })
		}

	/** The planner's comparison key: the same set of categories is the same membership; no categories compares as null. */
	fun version(snapshot: Snapshot): SyncConflictPlanner.Version = SyncConflictPlanner.Version(
		value = if (snapshot.categories.isEmpty()) null else snapshot.categories.joinToString("|", transform = { key(it) }),
		modifiedAt = snapshot.modifiedAt,
	)

	/** Conflicting titles, most recently touched first. */
	fun find(local: Map<Long, Snapshot>, incoming: Collection<Snapshot>, lastSyncAt: Long): List<Conflict> =
		incoming.distinctBy { it.mangaId }.mapNotNull { theirs ->
			val ours = local[theirs.mangaId] ?: return@mapNotNull null
			val decision = SyncConflictPlanner.decide(theirs.mangaId.toString(), version(ours), version(theirs), lastSyncAt)
			if (decision.action != SyncConflictPlanner.Action.CONFLICT) return@mapNotNull null
			val recommended = if (SyncConflictPlanner.resolveNewest(decision) == SyncConflictPlanner.Action.SEND_LOCAL) {
				Choice.KEEP_LOCAL
			} else {
				Choice.TAKE_INCOMING
			}
			Conflict(theirs.mangaId, theirs.title, ours, theirs, recommended)
		}.sortedByDescending { maxOf(it.local.modifiedAt, it.incoming.modifiedAt) }

	fun newestForAll(conflicts: List<Conflict>): Map<Long, Choice> =
		conflicts.associate { it.mangaId to it.recommended }

	fun keepLocalForAll(conflicts: List<Conflict>): Map<Long, Choice> =
		conflicts.associate { it.mangaId to Choice.KEEP_LOCAL }

	fun keepBothForAll(conflicts: List<Conflict>): Map<Long, Choice> =
		conflicts.associate { it.mangaId to Choice.KEEP_BOTH }

	/** Turns per-title choices into restore filters; a title without a choice takes its recommendation. */
	fun resolve(conflicts: List<Conflict>, choices: Map<Long, Choice>): Resolution {
		val keep = HashSet<Long>()
		val take = HashSet<Long>()
		val union = HashSet<Long>()
		for (conflict in conflicts) {
			when (choices[conflict.mangaId] ?: conflict.recommended) {
				Choice.KEEP_LOCAL -> keep += conflict.mangaId
				Choice.TAKE_INCOMING -> take += conflict.mangaId
				Choice.KEEP_BOTH -> union += conflict.mangaId
			}
		}
		return Resolution(keep, take, union)
	}

	/**
	 * "Keep both" restore rule for one incoming row: only a live row can add a category, and only when this device
	 * has no live row for it ([existingDeletedAt] is null when there is no row at all). Incoming removals are ignored.
	 */
	fun unionAddsRow(existingDeletedAt: Long?, incomingDeletedAt: Long): Boolean =
		incomingDeletedAt == 0L && (existingDeletedAt == null || existingDeletedAt != 0L)
}
