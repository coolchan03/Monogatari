package org.koitharu.kotatsu.builtin

import okhttp3.OkHttpClient
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder

/** Source registration stub. A working Gutendex parser is the following milestone. */
class GutenbergRepository(cache: MemoryContentCache, private val client: OkHttpClient) : CachingMangaRepository(cache) {
    override val source = BuiltinSource.GUTENBERG
    override val sortOrders = setOf(SortOrder.POPULARITY)
    override var defaultSortOrder = SortOrder.POPULARITY
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()
    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> = emptyList()
    override suspend fun getDetailsImpl(manga: Manga): Manga = manga
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> = emptyList()
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPageUrl(page: MangaPage): String = page.url
}
