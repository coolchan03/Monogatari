package org.koitharu.kotatsu.builtin.newpipe

import org.koitharu.kotatsu.builtin.BuiltinSource

/**
 * Chooses which NewPipe kiosks back the no-query (popular) list.
 *
 * YouTube removed its classic Trending page in July 2025. NewPipeExtractor v0.26.5 still lists
 * "Trending" but marks it deprecated, and defaults to "live" (running live streams), which is a
 * poor general list. The chart-based kiosks are preferred instead. PeerTube ("Trending") and
 * SoundCloud ("New & hot") use the extractor default.
 */
internal object NewPipeKioskPlan {
    const val MAX_ATTEMPTS = 3

    /** YouTube kiosk ids in v0.26.5, most general first. */
    val YOUTUBE_PREFERENCE = listOf(
        "trending_movies_and_shows",
        "trending_music",
        "trending_gaming",
        "Trending",
        "trending_podcasts_episodes",
        "live",
    )

    /**
     * Ordered kiosk ids to try, limited to the ones the service actually offers.
     * [lastWorking] is the kiosk that produced items last time, tried first to save failed requests.
     */
    fun candidates(
        source: BuiltinSource,
        available: Collection<String>,
        defaultId: String?,
        lastWorking: String? = null,
    ): List<String> {
        val ordered = buildList {
            lastWorking?.let(::add)
            if (source == BuiltinSource.YOUTUBE) addAll(YOUTUBE_PREFERENCE)
            defaultId?.let(::add)
            addAll(available.sorted())
        }
        return ordered.filter { it in available }.distinct().take(MAX_ATTEMPTS)
    }

    fun cacheKey(source: BuiltinSource, kioskId: String): String = source.name + "|kiosk|" + kioskId
}
