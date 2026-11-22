package org.koitharu.kotatsu.suggestions.domain

import org.koitharu.kotatsu.parsers.util.almostEquals
import java.util.Locale

/**
 * Treats differently written labels of the same genre as one ("Sci-Fi", "SciFi" and "Science Fiction"), so
 * recommendations and the tag blacklist do not depend on how each source spells a genre.
 *
 * Only labels that mean the same thing are merged. Related-but-different ones ("Shounen" vs "Shounen Ai", "Drama" vs
 * "Tragedy") stay apart, and anything not listed is only lower-cased and stripped of punctuation and spacing.
 */
object GenreLabels {

	/** Compact form (lower case letters and digits of any script) -> compact canonical form. */
	private val ALIASES: Map<String, String> = mapOf(
		"sciencefiction" to "scifi",
		"sf" to "scifi",
		"shonen" to "shounen",
		"shojo" to "shoujo",
		"martialart" to "martialarts",
		"romantic" to "romance",
		"humor" to "comedy",
		"humour" to "comedy",
		"sport" to "sports",
		"history" to "historical",
		"boyslove" to "yaoi",
		"superheroes" to "superhero",
	)

	fun canonical(title: String): String {
		val compact = buildString(title.length) {
			for (ch in title.lowercase(Locale.ROOT)) {
				if (ch.isLetterOrDigit()) append(ch)
			}
		}
		return ALIASES[compact] ?: compact
	}

	fun same(a: String, b: String): Boolean {
		val ca = canonical(a)
		return ca.isNotEmpty() && ca == canonical(b)
	}

	/** Same genre, or close enough by the existing fuzzy comparison. */
	fun matches(a: String, b: String, threshold: Float): Boolean = same(a, b) || a.almostEquals(b, threshold)
}
