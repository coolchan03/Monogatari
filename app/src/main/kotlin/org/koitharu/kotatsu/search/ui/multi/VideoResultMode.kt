package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.parsers.model.MangaSource

/** Visual video search selector. Only YouTube understands these NewPipe filters. */
enum class VideoResultMode { VIDEOS, CHANNELS, PLAYLISTS }

internal fun VideoResultMode.queryFor(source: MangaSource, query: String): String {
    if (source != BuiltinSource.YOUTUBE) return query
    val normalized = query.trim()
    val term = if (normalized.startsWith("channel:", true) || normalized.startsWith("playlist:", true)) {
        normalized.substringAfter(':').trim()
    } else normalized
    return when (this) {
        VideoResultMode.VIDEOS -> term
        VideoResultMode.CHANNELS -> "channel:" + term
        VideoResultMode.PLAYLISTS -> "playlist:" + term
    }
}
