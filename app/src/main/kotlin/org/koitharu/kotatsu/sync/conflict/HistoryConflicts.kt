package org.koitharu.kotatsu.sync.conflict

/**
 * Reading-progress conflicts between this device's history and a received library, built on [SyncConflictPlanner].
 * A title is a conflict only when both devices have it and both changed it since their last sync (or neither did,
 * yet they differ). Titles only one side has, or one-sided changes, merge normally without asking.
 */
object HistoryConflicts {

	/** One device's history row for a title, reduced to what the planner and the review screen need. */
	data class Snapshot(
		val mangaId: Long,
		val title: String,
		val chapterId: Long,
		val page: Int,
		val percent: Float,
		val updatedAt: Long,
		val deletedAt: Long,
	) {
		val isDeleted: Boolean
			get() = deletedAt != 0L

		val modifiedAt: Long
			get() = maxOf(updatedAt, deletedAt)
	}

	enum class Choice { KEEP_LOCAL, TAKE_INCOMING }

	data class Conflict(
		val mangaId: Long,
		val title: String,
		val local: Snapshot,
		val incoming: Snapshot,
		/** The planner's "newest wins" pick, selected by default. */
		val recommended: Choice,
	)

	/** Manga ids whose local history must survive the merge, and ids whose incoming history must replace it. */
	data class Resolution(val keepLocal: Set<Long>, val takeIncoming: Set<Long>) {

		companion object {
			val NONE = Resolution(emptySet(), emptySet())
		}
	}

	/** The planner's comparison key: same chapter and page means the same progress; deletions compare as null. */
	fun version(snapshot: Snapshot): SyncConflictPlanner.Version = SyncConflictPlanner.Version(
		value = if (snapshot.isDeleted) null else "${snapshot.chapterId}#${snapshot.page}",
		modifiedAt = snapshot.modifiedAt,
	)

	/** Conflicting titles, most recently touched first. [local] is keyed by manga id. */
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

	/** Turns per-title choices into restore filters; a title without a choice takes its recommendation. */
	fun resolve(conflicts: List<Conflict>, choices: Map<Long, Choice>): Resolution {
		val keep = HashSet<Long>()
		val take = HashSet<Long>()
		for (conflict in conflicts) {
			when (choices[conflict.mangaId] ?: conflict.recommended) {
				Choice.KEEP_LOCAL -> keep += conflict.mangaId
				Choice.TAKE_INCOMING -> take += conflict.mangaId
			}
		}
		return Resolution(keep, take)
	}

	/** "12" for whole chapter numbers, "12.5" otherwise; null when the number is unknown. */
	fun formatChapterNumber(number: Float?): String? = when {
		number == null || number <= 0f || number.isNaN() -> null
		number % 1f == 0f -> number.toInt().toString()
		else -> number.toString()
	}
}
