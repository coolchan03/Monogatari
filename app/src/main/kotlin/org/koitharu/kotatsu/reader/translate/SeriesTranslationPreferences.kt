package org.koitharu.kotatsu.reader.translate

/** Pure domain rules used before adding reader UI, workers, or model downloads. */
internal object SeriesTranslationPreferences {
    /** A user's explicit decision overrides automatic language-based defaults. */
    fun enabled(explicit: Boolean?, sourceLanguage: String?, targetLanguage: String = "en"): Boolean =
        explicit ?: (sourceLanguage?.substringBefore('-')?.lowercase()
            ?.takeIf { it.length in 2..3 }?.let { it != targetLanguage.substringBefore('-').lowercase() } ?: false)

    /** Prefer a specific longest phrase match to avoid replacing names inside other names. */
    fun glossaryReplace(text: String, glossary: Map<String, String>): String {
        val replacements = glossary.entries.filter { it.key.isNotBlank() && it.value.isNotEmpty() }
            .sortedByDescending { it.key.length }
        if (replacements.isEmpty()) return text
        val expression = Regex(replacements.joinToString("|") { Regex.escape(it.key) })
        return expression.replace(text) { match ->
            replacements.firstOrNull { it.key == match.value }?.value ?: match.value
        }
    }
}
