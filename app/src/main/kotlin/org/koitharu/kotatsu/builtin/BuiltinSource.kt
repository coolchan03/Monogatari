package org.koitharu.kotatsu.builtin

import org.koitharu.kotatsu.parsers.model.MangaSource

/** Built-in non-parser catalogs, persisted by stable name. */
enum class BuiltinSource(override val name: String, val title: String) : MangaSource {
    GUTENBERG("builtin:gutenberg", "Project Gutenberg");
    companion object {
        fun fromName(name: String) = entries.firstOrNull { it.name == name }
    }
}
