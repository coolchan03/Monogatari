package org.koitharu.kotatsu.reader.domain

/**
 * Tracks pages being loaded *speculatively* so that work for a position the user has left can be
 * cancelled. A page the user actually asked for ([markDemanded]) is never reported as obsolete.
 */
internal class PrefetchTracker {

	private val inFlight = HashSet<Long>()

	@Synchronized
	fun markPrefetching(id: Long) {
		inFlight.add(id)
	}

	/** The visible reader needs this page now: it stops being speculative work. */
	@Synchronized
	fun markDemanded(id: Long) {
		inFlight.remove(id)
	}

	@Synchronized
	fun markFinished(id: Long) {
		inFlight.remove(id)
	}

	/** Returns (and forgets) speculative loads that are not part of the new [window]. */
	@Synchronized
	fun takeObsolete(window: Collection<Long>): List<Long> {
		val keep = window.toHashSet()
		val obsolete = inFlight.filter { it !in keep }
		inFlight.removeAll(obsolete.toSet())
		return obsolete
	}

	@Synchronized
	fun size(): Int = inFlight.size
}
