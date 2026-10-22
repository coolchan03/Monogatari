package org.koitharu.kotatsu.discover.taste

import java.util.Locale
import kotlin.math.ln

/**
 * Draft (v1.3) on-device taste profile for personalized Discover. Built only from the user's own library and
 * history on the device; nothing is uploaded. Tags read recently and favourites weigh more; dropped titles weigh less.
 */
class TasteProfile private constructor(private val weights: Map<String, Double>) {

	data class Signal(val tags: Collection<String>, val favourite: Boolean, val readChapters: Int, val dropped: Boolean, val ageDays: Int)

	data class Candidate<T>(val item: T, val tags: Collection<String>, val alreadyInLibrary: Boolean)

	fun weightOf(tag: String): Double = weights[normalize(tag)] ?: 0.0

	/** The strongest tags, for explaining recommendations ("Because you read fantasy, isekai"). */
	fun topTags(limit: Int = 5): List<String> = weights.entries.filter { it.value > 0 }
		.sortedByDescending { it.value }.take(limit).map { it.key }

	/** Highest-scoring candidates not already in the library; ties keep the provider's order. */
	fun <T> rank(candidates: List<Candidate<T>>, limit: Int = 30): List<T> = candidates
		.withIndex()
		.filterNot { it.value.alreadyInLibrary }
		.map { (index, candidate) -> Triple(candidate.item, score(candidate.tags), index) }
		.filter { it.second > 0.0 }
		.sortedWith(compareByDescending<Triple<T, Double, Int>> { it.second }.thenBy { it.third })
		.take(limit)
		.map { it.first }

	fun score(tags: Collection<String>): Double {
		val distinct = tags.map(::normalize).distinct()
		if (distinct.isEmpty()) return 0.0
		// Average keeps long tag lists from dominating; a small bonus rewards several matching tags.
		val matched = distinct.map { weights[it] ?: 0.0 }
		return matched.sum() / distinct.size + 0.1 * matched.count { it > 0 }
	}

	companion object {

		fun build(signals: List<Signal>): TasteProfile {
			val weights = HashMap<String, Double>()
			for (signal in signals) {
				val recency = 1.0 / (1.0 + signal.ageDays / 30.0)
				val engagement = (if (signal.favourite) 2.0 else 0.0) + ln(1.0 + signal.readChapters)
				val value = (if (signal.dropped) -0.5 else 1.0) * engagement * recency
				for (tag in signal.tags.map(::normalize).distinct()) {
					weights[tag] = (weights[tag] ?: 0.0) + value
				}
			}
			val max = weights.values.maxOrNull()?.takeIf { it > 0 } ?: 1.0
			return TasteProfile(weights.mapValues { it.value / max })
		}

		private val SEPARATORS = Regex("[\\s_-]+")
		private val APOSTROPHES = Regex("['\u2019`]")

		/**
		 * Source tags and catalog genres name the same thing differently ("Yaoi", "Shounen Ai", AniList "Boys' Love",
		 * MyAnimeList "Boys Love"); without these aliases the "For you" ranking found no overlap and showed popular order.
		 */
		private val ALIASES = mapOf(
			"yaoi" to "boys love",
			"bl" to "boys love",
			"shounen ai" to "boys love",
			"shonen ai" to "boys love",
			"danmei" to "boys love",
			"yuri" to "girls love",
			"shoujo ai" to "girls love",
			"shojo ai" to "girls love",
			"gl" to "girls love",
			"scifi" to "sci fi",
			"science fiction" to "sci fi",
			"school life" to "school",
			"gender bender" to "gender bending",
			"martial art" to "martial arts",
			"superpower" to "super power",
			"rom com" to "romantic comedy",
			"romcom" to "romantic comedy",
		)

		internal fun normalize(tag: String): String {
			val plain = tag.trim().lowercase(Locale.ROOT)
				.replace(APOSTROPHES, "")
				.replace(SEPARATORS, " ")
			return ALIASES[plain] ?: plain
		}
	}
}
