package org.koitharu.kotatsu.search.ui.multi

/** Narrows global results to one language across every media type (pure, unit-tested). */
internal object LanguageFilter {

	/** Sources with no single language (multi-language or unknown) stay visible; a chosen language hides only the others. */
	fun matches(sourceLanguage: String?, selected: String?): Boolean =
		selected == null || sourceLanguage.isNullOrBlank() || sourceLanguage.equals(selected, ignoreCase = true)

	/** Distinct language codes present, sorted, ignoring blanks. */
	fun available(sourceLanguages: List<String?>): List<String> =
		sourceLanguages.mapNotNull { it?.lowercase()?.takeIf(String::isNotBlank) }.distinct().sorted()
}
