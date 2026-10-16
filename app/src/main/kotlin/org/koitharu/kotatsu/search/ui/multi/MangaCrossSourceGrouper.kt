package org.koitharu.kotatsu.search.ui.multi

import android.content.Context
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.mediaType
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.parsers.model.Manga

/** Presentation-only deduplication; never merges chapter lists, history, or library records. */
internal class MangaCrossSourceGrouper(context: Context) {
    private val preferences = context.getSharedPreferences("preferred_manga_source", Context.MODE_PRIVATE)

    private fun candidates(rows: List<MangaListModel>) = rows.map { row ->
        val manga = row.manga
        MangaDuplicatePolicy.Candidate(
            manga.id, manga.source.name, manga.title, manga.altTitle,
            manga.authors, manga.source.mediaType().name,
        )
    }

    private fun groups(rows: List<MangaListModel>) =
        MangaDuplicatePolicy.groups(candidates(rows))

    private fun identity(manga: Manga) = manga.id to manga.source.name

    private fun preferred(group: MangaDuplicatePolicy.Group) =
        MangaDuplicatePolicy.preferred(group, preferences.getString(group.key, null))
    /** Mark a preferred copy per matched work without erasing either source. */
    fun choose(sections: List<SearchResultsListModel>, manga: Manga) {
        val group = findGroup(sections, manga) ?: return
        if (group.copies.none { it.id to it.source == identity(manga) }) return
        preferences.edit().putString(group.key, manga.source.name).apply()
    }

    fun preferredName(sections: List<SearchResultsListModel>, manga: Manga): String? =
        findGroup(sections, manga)?.let { preferences.getString(it.key, null) }

    fun alternatives(sections: List<SearchResultsListModel>, manga: Manga): List<Manga> {
        val rows = sections.flatMap { it.list }
        val group = findGroup(sections, manga) ?: return emptyList()
        val byIdentity = rows.associateBy { it.manga.id to it.manga.source.name }
        return group.copies.mapNotNull { byIdentity[it.id to it.source]?.manga }
    }

    private fun findGroup(sections: List<SearchResultsListModel>, manga: Manga) =
        groups(sections.flatMap { it.list }).firstOrNull { group ->
            group.copies.any { it.id to it.source == identity(manga) }
        }
    /** Put a single preferred representative into a combined section; preserve unsupported/failed sources. */
    fun group(sections: List<SearchResultsListModel>): List<SearchResultsListModel> {
        val rows = sections.flatMap { it.list }
        val matches = groups(rows)
        if (matches.isEmpty()) return sections
        val byIdentity = rows.associateBy { it.manga.id to it.manga.source.name }
        val hidden = matches.flatMap { it.copies }.mapTo(HashSet()) { it.id to it.source }
        val representatives = matches.mapNotNull { group ->
            val candidate = preferred(group)
            byIdentity[candidate.id to candidate.source]
        }
        if (representatives.isEmpty()) return sections
        val groupedSection = SearchResultsListModel(
            titleResId = R.string.search_cross_source_matches,
            source = UnknownMangaSource,
            listFilter = null,
            sortOrder = null,
            list = representatives,
            error = null,
        )
        return listOf(groupedSection) + sections.map { section ->
            section.copy(list = section.list.filterNot {
                it.manga.id to it.manga.source.name in hidden
            })
        }
    }
}
