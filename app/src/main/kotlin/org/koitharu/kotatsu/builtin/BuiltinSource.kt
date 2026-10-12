package org.koitharu.kotatsu.builtin

import org.koitharu.kotatsu.parsers.model.MangaSource

/** Stable persisted source IDs; not an enum because Enum.name cannot be overridden. */
sealed class BuiltinSource(
    override val name: String,
    val title: String,
) : MangaSource {
    data object GUTENBERG : BuiltinSource("builtin:gutenberg", "Project Gutenberg")

    companion object {
        val entries: List<BuiltinSource> = listOf(GUTENBERG)
        fun fromName(name: String): BuiltinSource? = entries.firstOrNull { it.name == name }
    }
}
