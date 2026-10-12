package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.parsers.model.MangaSource

/** Visual video search selector. Only YouTube understands these NewPipe filters. */
enum class VideoResultMode { VIDEOS, CHANNELS, PLAYLISTS }

internal fun VideoResultMode.queryFor(source: MangaSource, query: String): String {
    if (source != BuiltinSource.YOUTUBE || this == VideoResultMode.VIDEOS) return query
    val normalized = query.trim()
    if (normalized.startsWith("channel:", true) || normalized.startsWith("playlist:", true)) {
        return normalized.substringAfter(':').let { term ->
            (if (this == VideoResultMode.CHANNELS) "channel:" else "playlist:") + term
        }
    }
    return (if (this == VideoResultMode.CHANNELS) "channel:" else "playlist:") + normalized
}
