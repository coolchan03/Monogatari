package org.koitharu.kotatsu.builtin

/**
 * Genre browsing for Project Gutenberg's public-domain fiction: `genre:horror`, `genre:"science fiction"` or `genre:romance`
 * in the search box becomes Gutendex's `topic=` filter (pure and unit-tested).
 */
internal object GutenbergGenres {

	data class Parsed(val text: String, val topic: String?)

	private val TOKEN = Regex("""genre:(?:"([^"]+)"|(\S+))""", RegexOption.IGNORE_CASE)

	/** Short names people type, mapped to the subject words Gutenberg uses. */
	private val ALIASES = mapOf(
		"sf" to "science fiction", "scifi" to "science fiction", "sci-fi" to "science fiction",
		"scary" to "horror", "ghost" to "horror", "love" to "romance",
		"detective" to "mystery", "crime" to "mystery", "kids" to "children",
	)

	fun parse(query: String): Parsed {
		val match = TOKEN.find(query) ?: return Parsed(query.trim(), null)
		val raw = (match.groupValues[1].ifEmpty { match.groupValues[2] }).trim().lowercase()
		val topic = ALIASES[raw] ?: raw
		val text = query.removeRange(match.range).replace(Regex("\\s+"), " ").trim()
		return Parsed(text, topic.takeIf { it.isNotBlank() })
	}
}
