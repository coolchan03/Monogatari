package org.koitharu.kotatsu.core.qa

import java.net.URI

/** Pure helpers for the News screen's source filter and topic chips (unit-tested). */
internal object NewsFilters {

	/** Site name of an article link without "www.", or null when the link isn't a web address. */
	fun host(link: String): String? = runCatching { URI(link.trim()).host }.getOrNull()
		?.removePrefix("www.")?.takeIf { it.isNotBlank() }

	fun sources(links: List<String>): List<String> = links.mapNotNull(::host).distinct().sorted()

	/** Cycles "all sources" (null) through each source and back to all. */
	fun nextSource(current: String?, all: List<String>): String? {
		if (all.isEmpty()) return null
		if (current == null) return all.first()
		val index = all.indexOf(current)
		return if (index < 0 || index == all.lastIndex) null else all[index + 1]
	}

	fun matchesTag(entryTags: List<String>, tag: String?): Boolean =
		tag == null || entryTags.any { it.equals(tag, ignoreCase = true) }

	fun matchesSource(link: String, source: String?): Boolean = source == null || host(link) == source
}
