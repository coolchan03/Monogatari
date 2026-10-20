package org.koitharu.kotatsu.sync.conflict

/**
 * Draft (v1.2) two-device merge planning for reading progress, favourites and settings.
 *
 * Each record is compared with the state both devices agreed on at their last sync. A side that did not change since
 * then simply receives the other side's change; when both changed the same record differently, the record is a
 * conflict the user resolves (nothing is silently overwritten). Deletions are changes too.
 */
object SyncConflictPlanner {

	/** One device's copy of a record; [value] is a stable comparison key (e.g. "chapter#page"), null when deleted. */
	data class Version(val value: String?, val modifiedAt: Long)

	enum class Action { KEEP, TAKE_INCOMING, SEND_LOCAL, CONFLICT }

	data class Decision(val key: String, val action: Action, val local: Version?, val incoming: Version?)

	/**
	 * @param lastSyncAt when the two devices last finished a sync with each other (0 for the first sync).
	 */
	fun plan(local: Map<String, Version>, incoming: Map<String, Version>, lastSyncAt: Long): List<Decision> =
		(local.keys + incoming.keys).sorted().map { key -> decide(key, local[key], incoming[key], lastSyncAt) }

	fun decide(key: String, local: Version?, incoming: Version?, lastSyncAt: Long): Decision {
		val action = when {
			local?.value == incoming?.value -> Action.KEEP
			local == null -> Action.TAKE_INCOMING
			incoming == null -> Action.SEND_LOCAL
			else -> {
				val localChanged = local.modifiedAt > lastSyncAt
				val incomingChanged = incoming.modifiedAt > lastSyncAt
				when {
					localChanged && incomingChanged -> Action.CONFLICT
					incomingChanged -> Action.TAKE_INCOMING
					localChanged -> Action.SEND_LOCAL
					// Neither side changed since the last sync yet they differ: older data, ask rather than guess.
					else -> Action.CONFLICT
				}
			}
		}
		return Decision(key, action, local, incoming)
	}

	/** "Newest wins" resolution offered as a one-tap choice for all conflicts; deletions win exact ties. */
	fun resolveNewest(decision: Decision): Action {
		if (decision.action != Action.CONFLICT) return decision.action
		val local = decision.local ?: return Action.TAKE_INCOMING
		val incoming = decision.incoming ?: return Action.SEND_LOCAL
		return when {
			incoming.modifiedAt > local.modifiedAt -> Action.TAKE_INCOMING
			incoming.modifiedAt < local.modifiedAt -> Action.SEND_LOCAL
			incoming.value == null -> Action.TAKE_INCOMING
			else -> Action.SEND_LOCAL
		}
	}
}
