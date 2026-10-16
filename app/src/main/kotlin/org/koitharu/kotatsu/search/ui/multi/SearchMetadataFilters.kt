package org.koitharu.kotatsu.search.ui.multi

/** Only real provider tags, catalog formats, and local reading history are filterable. */
internal object SearchMetadataFilters {
    enum class History { ANY, VIEWED, UNVIEWED }

    fun tagMatches(tags: Collection<String>, selected: String?): Boolean =
        selected.isNullOrBlank() || tags.any { it.contains(selected.trim(), ignoreCase = true) }

    fun historyMatches(id: Long, selected: History, viewedIds: Set<Long>?): Boolean = when (selected) {
        History.ANY -> true
        History.VIEWED -> viewedIds != null && id in viewedIds
        History.UNVIEWED -> viewedIds != null && id !in viewedIds
    }

    fun languageMatches(language: String?, selected: String?): Boolean =
        selected.isNullOrBlank() || language.isNullOrBlank() ||
            language.equals(selected, ignoreCase = true) ||
            language.equals(java.util.Locale.forLanguageTag(selected).getDisplayLanguage(java.util.Locale.ENGLISH), ignoreCase = true)

    fun formatMatches(format: String?, selected: String?): Boolean =
        selected.isNullOrBlank() || (format?.contains(selected, ignoreCase = true) == true)

    fun availableTags(tags: Iterable<String>): List<String> = tags
        .map(String::trim).filter(String::isNotEmpty)
        .distinctBy(String::lowercase).sorted().take(50)

    fun availableFormats(formats: Iterable<String?>): List<String> = formats
        .filterNotNull().map(String::trim).filter(String::isNotEmpty)
        .distinctBy(String::lowercase).sorted().take(25)
}
