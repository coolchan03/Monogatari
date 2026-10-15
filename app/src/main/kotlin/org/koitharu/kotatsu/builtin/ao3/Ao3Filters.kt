package org.koitharu.kotatsu.builtin.ao3

/**
 * AO3 search filters typed into the search box: `fandom:"Given" ship:"A/B" rating:teen lang:es updated:week`.
 * Anything that isn't a recognised `key:value` token stays in the plain-text query (pure and unit-tested).
 */
internal object Ao3Filters {

	data class Parsed(
		val text: String,
		val fandoms: List<String> = emptyList(),
		val ships: List<String> = emptyList(),
		/** AO3 rating ids (9 not rated, 10 general, 11 teen, 12 mature, 13 explicit); null = not constrained. */
		val ratingIds: List<Int>? = null,
		val language: String? = null,
		/** AO3 date expression for "last updated", e.g. "< 1 week". */
		val updated: String? = null,
	)

	private val TOKEN = Regex("""(\w+):(?:"([^"]+)"|(\S+))""")
	private val LANGUAGE = Regex("[a-z]{2,3}(-[A-Za-z]{2,4})?")

	private val RATINGS = mapOf(
		"notrated" to 9, "unrated" to 9, "general" to 10, "g" to 10, "teen" to 11, "t" to 11,
		"mature" to 12, "m" to 12, "explicit" to 13, "e" to 13,
	)

	fun parse(query: String): Parsed {
		var fandoms = emptyList<String>()
		var ships = emptyList<String>()
		var ratings: List<Int>? = null
		var language: String? = null
		var updated: String? = null
		val text = TOKEN.replace(query) { match ->
			val key = match.groupValues[1].lowercase()
			val value = (match.groupValues[2].ifEmpty { match.groupValues[3] }).trim()
			when (key) {
				"fandom" -> fandoms = fandoms + value
				"ship", "relationship" -> ships = ships + value
				"rating" -> {
					val ids = value.split(',', '/').mapNotNull { RATINGS[it.trim().lowercase()] }
					if (ids.isEmpty()) return@replace match.value
					ratings = (ratings.orEmpty() + ids).distinct()
				}
				"lang", "language" -> {
					if (!LANGUAGE.matches(value)) return@replace match.value
					language = value
				}
				"updated" -> {
					val parsed = updatedExpression(value) ?: return@replace match.value
					updated = parsed
				}
				else -> return@replace match.value
			}
			""
		}.replace(Regex("\\s+"), " ").trim()
		return Parsed(text, fandoms, ships, ratings, language, updated)
	}

	/** "day", "week", "month", "year" or "30d" -> AO3's date syntax. */
	fun updatedExpression(value: String): String? {
		return when (val v = value.lowercase()) {
			"day", "today" -> "< 1 day"
			"week" -> "< 1 week"
			"month" -> "< 1 month"
			"year" -> "< 1 year"
			else -> Regex("(\\d{1,4})d").matchEntire(v)?.let { "< ${it.groupValues[1]} days" }
		}
	}

	/**
	 * Ratings to search: what the user asked for, limited to what their content level allows. An empty list means
	 * nothing may be shown (they asked only for ratings their content level hides).
	 */
	fun effectiveRatings(requested: List<Int>?, allowed: List<Int>): List<Int> =
		if (requested == null) allowed else requested.filter { it in allowed }
}
