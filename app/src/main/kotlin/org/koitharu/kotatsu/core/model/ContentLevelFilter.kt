package org.koitharu.kotatsu.core.model

import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga

/** Apply title ratings, not the source's coarse adult-only flag. */
object ContentLevelFilter {
    fun allows(level: String, rating: ContentRating?): Boolean = when (level) {
        "SAFE" -> rating == null || rating == ContentRating.SAFE
        "SUGGESTIVE" -> rating != ContentRating.ADULT
        else -> true
    }

    fun allows(level: String, manga: Manga): Boolean = allows(level, manga.contentRating)
}
