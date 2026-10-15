package org.koitharu.kotatsu.builtin

/**
 * Curated Internet Archive film and audio collections reachable from the search box: `collection:feature_films`,
 * or a friendly word such as `collection:cartoons`, `collection:silent`, `collection:trailers` (pure and unit-tested).
 */
internal object ArchiveCollections {

	data class Parsed(val term: String, val collection: String?)

	/** Friendly names to Internet Archive collection identifiers (all public, freely playable collections). */
	val aliases = mapOf(
		"films" to "feature_films", "feature" to "feature_films", "movies" to "feature_films",
		"silent" to "silent_films", "cartoons" to "animationandcartoons", "animation" to "animationandcartoons",
		"tv" to "classic_tv", "classic-tv" to "classic_tv", "trailers" to "movie_trailers",
		"community" to "opensource_movies", "fan" to "opensource_movies", "indie" to "opensource_movies",
		"noir" to "film_noir", "scifi" to "SciFi_Horror", "horror" to "SciFi_Horror",
		"radio" to "oldtimeradio", "audiodrama" to "oldtimeradio",
	)

	private val TOKEN = Regex("""collection:([A-Za-z0-9_\-]{2,60})""", RegexOption.IGNORE_CASE)

	fun parse(query: String): Parsed {
		val match = TOKEN.find(query) ?: return Parsed(query.trim(), null)
		val raw = match.groupValues[1]
		val id = aliases[raw.lowercase()] ?: raw
		return Parsed(query.removeRange(match.range).replace(Regex("\\s+"), " ").trim(), id)
	}
}
