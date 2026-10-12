package org.koitharu.kotatsu.builtin.newpipe

import org.koitharu.kotatsu.builtin.BuiltinSource

/** Normalizes search mode without exposing unsupported filters to other services. */
internal object NewPipeSearchPlan {
    data class Plan(val query: String, val filter: List<String>, val cacheKey: String)
    fun create(source: BuiltinSource, text: String?): Plan {
        val raw = text.orEmpty().trim()
        val defaultQuery = when (source) {
            BuiltinSource.SOUNDCLOUD -> "audio drama"
            BuiltinSource.PEERTUBE -> "animation"
            else -> "BL drama"
        }
        val requested = raw.ifBlank { defaultQuery }
        val mode = if (source == BuiltinSource.YOUTUBE) {
            when {
                requested.startsWith("playlist:", ignoreCase = true) -> "playlists"
                requested.startsWith("channel:", ignoreCase = true) -> "channels"
                else -> "videos"
            }
        } else "videos"
        val stripped = if (mode == "videos") requested else requested.substringAfter(':').trim()
        val normalized = stripped.ifBlank { defaultQuery }
        val filters = if (source == BuiltinSource.YOUTUBE) listOf(mode) else emptyList()
        return Plan(normalized, filters, source.name + "|" + mode + "|" + normalized.lowercase())
    }
}
