package org.koitharu.kotatsu.builtin

import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.core.media.MediaType

/** Stable persisted source IDs; not an enum because Enum.name cannot be overridden. */
sealed class BuiltinSource(
    override val name: String,
    val title: String,
    val type: MediaType,
) : MangaSource {
    data object GUTENBERG : BuiltinSource("builtin:gutenberg", "Project Gutenberg", MediaType.BOOK)
    data object IA_BOOKS : BuiltinSource("builtin:ia_books", "Internet Archive Books", MediaType.BOOK)
    data object IA_VIDEO : BuiltinSource("builtin:ia_video", "Internet Archive Video", MediaType.VIDEO)
    data object IA_AUDIO : BuiltinSource("builtin:ia_audio", "Internet Archive Audio", MediaType.AUDIO)
    data object IA_COMICS : BuiltinSource("builtin:ia_comics", "Internet Archive Comics", MediaType.MANGA)

    companion object {
        val entries: List<BuiltinSource> = listOf(GUTENBERG, IA_BOOKS, IA_VIDEO, IA_AUDIO, IA_COMICS)
        fun fromName(name: String): BuiltinSource? = entries.firstOrNull { it.name == name }
    }
}
