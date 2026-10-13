package org.koitharu.kotatsu.stats.data

/** A SQL projection: normalized tag title and its inclusive reading duration. */
data class TagDurationRow(
    val title: String,
    val durationMs: Long,
)
