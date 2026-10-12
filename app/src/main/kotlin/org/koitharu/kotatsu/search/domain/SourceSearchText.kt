package org.koitharu.kotatsu.search.domain

import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.parsers.model.MangaSource

/** Match and rank results using the actual title query, not NewPipe's search-mode syntax. */
internal fun MangaSource.searchTextForResults(query: String): String {
    if (this != BuiltinSource.YOUTUBE) return query
    return when {
        query.startsWith("channel:", ignoreCase = true) -> query.substringAfter(':').trim()
        query.startsWith("playlist:", ignoreCase = true) -> query.substringAfter(':').trim()
        else -> query
    }
}
