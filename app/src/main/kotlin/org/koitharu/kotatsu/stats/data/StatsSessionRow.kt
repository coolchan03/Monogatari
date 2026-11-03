package org.koitharu.kotatsu.stats.data

/** A SQL projection: one reading session with the persisted name of its source. */
data class StatsSessionRow(
	val startedAt: Long,
	val duration: Long,
	val source: String,
)
