package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.parsers.model.ContentType

/** What an illustrated title is, as far as the source or its own tags say. UNKNOWN is never guessed away. */
enum class IllustratedFormat { MANGA, WEBTOON, COMICS, MANHWA, MANHUA, UNKNOWN }

/** The format chip the user picks inside manga search. [ALL] means no format filtering at all. */
enum class FormatFacet(val formats: Set<IllustratedFormat>) {
	ALL(emptySet()),
	MANGA(setOf(IllustratedFormat.MANGA)),
	WEBTOON(setOf(IllustratedFormat.WEBTOON)),
	COMICS(setOf(IllustratedFormat.COMICS)),
	MANHWA(setOf(IllustratedFormat.MANHWA)),
	MANHUA(setOf(IllustratedFormat.MANHUA));

	companion object {
		fun fromName(raw: String?): FormatFacet = entries.firstOrNull { it.name == raw } ?: ALL
	}
}

/**
 * Pure rules for the manga/webtoon/comics facet. The parser contract has no Webtoon content type (its MANGA
 * type includes webtoons), so a title is a webtoon only when its own tags say so. Korean origin or a vertical
 * reader mode never implies it, and anything without evidence is [IllustratedFormat.UNKNOWN].
 */
object SearchContentFormat {

	private val webtoonTags = setOf("webtoon", "webtoons", "long strip", "longstrip", "web toon")
	private val comicsTags = setOf("comic", "comics", "western comic", "western comics")
	private val manhwaTags = setOf("manhwa")
	private val manhuaTags = setOf("manhua")
	private val mangaTags = setOf("manga")

	/** Formats a title's own tags declare explicitly; empty when the tags say nothing about format. */
	fun fromTags(tags: Collection<String>): Set<IllustratedFormat> {
		val result = LinkedHashSet<IllustratedFormat>()
		for (raw in tags) {
			val tag = raw.trim().lowercase()
			when (tag) {
				in webtoonTags -> result += IllustratedFormat.WEBTOON
				in comicsTags -> result += IllustratedFormat.COMICS
				in manhwaTags -> result += IllustratedFormat.MANHWA
				in manhuaTags -> result += IllustratedFormat.MANHUA
				in mangaTags -> result += IllustratedFormat.MANGA
			}
		}
		return result
	}

	/** What a source declares for all of its titles; null/OTHER/etc. say nothing about format. */
	fun fromSourceType(type: ContentType?): IllustratedFormat? = when (type) {
		ContentType.MANGA -> IllustratedFormat.MANGA
		ContentType.COMICS -> IllustratedFormat.COMICS
		ContentType.MANHWA -> IllustratedFormat.MANHWA
		ContentType.MANHUA -> IllustratedFormat.MANHUA
		else -> null
	}

	/** Tags are per-title evidence and win over the coarser source declaration. Never empty. */
	fun classify(sourceType: ContentType?, tags: Collection<String>): Set<IllustratedFormat> {
		val explicit = fromTags(tags)
		if (explicit.isNotEmpty()) return explicit
		return fromSourceType(sourceType)?.let { setOf(it) } ?: setOf(IllustratedFormat.UNKNOWN)
	}

	/**
	 * @param includeUnclassified keeps titles whose format is unknown while a restrictive facet is active, so
	 * sources without format metadata are not silently dropped.
	 */
	fun matches(facet: FormatFacet, includeUnclassified: Boolean, formats: Set<IllustratedFormat>): Boolean {
		if (facet == FormatFacet.ALL) return true
		if (formats.any { it in facet.formats }) return true
		return includeUnclassified && IllustratedFormat.UNKNOWN in formats
	}
}
