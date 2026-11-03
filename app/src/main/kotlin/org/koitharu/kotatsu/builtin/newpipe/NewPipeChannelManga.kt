package org.koitharu.kotatsu.builtin.newpipe

import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaTag
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN

/**
 * The [Manga] a YouTube channel has in [NewPipeVideoRepository]: same url, id and "channel" tag as a
 * search result, so a channel imported from a file and one followed from search are the same entry.
 * Videos (chapters) and the cover load lazily when the channel is opened.
 */
internal object NewPipeChannelManga {

    fun youtube(channelId: String, name: String): Manga {
        val source = BuiltinSource.YOUTUBE
        val url = "https://www.youtube.com/channel/$channelId"
        return Manga(
            id = ArchiveMapping.uid(source, url),
            title = name,
            altTitles = emptySet(),
            url = url,
            publicUrl = url,
            rating = RATING_UNKNOWN,
            contentRating = ContentRating.SUGGESTIVE,
            coverUrl = null,
            tags = setOf(MangaTag("channel", "channel", source)),
            state = null,
            authors = emptySet(),
            largeCoverUrl = null,
            description = "",
            chapters = null,
            source = source,
        )
    }
}
