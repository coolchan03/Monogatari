package org.koitharu.kotatsu.builtin.opds

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.builtin.ArchiveMapping
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.media.OpdsCatalog
import org.koitharu.kotatsu.core.media.epub.EpubBook
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.RATING_UNKNOWN
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.File

/** Saved OPDS catalog as a persistent BOOK source; EPUB acquisitions read locally. */
class OpdsRepository(
    override val source: OpdsSource,
    cache: MemoryContentCache,
    private val catalog: OpdsCatalog,
    private val client: OkHttpClient,
    private val context: Context,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    override val sortOrders = setOf(SortOrder.POPULARITY)
    override var defaultSortOrder = SortOrder.POPULARITY
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        if (offset > 0) return emptyList()
        val query = filter?.query?.trim().orEmpty()
        return catalog.load(source.url)
            .filter { it.acquisition && (query.isEmpty() || it.title.contains(query, ignoreCase = true)) }
            .map { entry ->
                Manga(
                    id = ArchiveMapping.uid(source, entry.href),
                    title = entry.title,
                    altTitles = emptySet(),
                    url = entry.href,
                    publicUrl = entry.href,
                    rating = RATING_UNKNOWN,
                    contentRating = ContentRating.SAFE,
                    coverUrl = null,
                    tags = emptySet(),
                    state = null,
                    authors = setOf(entry.author).filter(String::isNotBlank).toSet(),
                    largeCoverUrl = null,
                    description = null,
                    chapters = null,
                    source = source,
                )
            }
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val book = EpubBook.open(download(manga.url))
        return manga.copy(chapters = book.chapters.mapIndexed { index, chapter ->
            val path = manga.url + "#epub:" + chapter.href
            MangaChapter(
                id = ArchiveMapping.uid(source, path),
                title = chapter.title,
                number = (index + 1).toFloat(),
                volume = 0,
                url = path,
                scanlator = null,
                uploadDate = 0L,
                branch = null,
                source = source,
            )
        })
    }

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String {
        val href = chapter.url.substringAfter("#epub:", "")
        if (href.isEmpty()) return ""
        val file = download(chapter.url.substringBefore("#epub:"))
        return EpubBook.open(file).chapterHtml(href)
    }
    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> = emptyList()
    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> =
        listOf(MangaPage(ArchiveMapping.uid(source, chapter.url), chapter.url, null, source))
    override suspend fun getPageUrl(page: MangaPage): String = page.url
    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()

    private suspend fun download(url: String): File = withContext(Dispatchers.IO) {
        require(url.startsWith("https://") || url.startsWith("http://"))
        val cacheDir = File(context.cacheDir, "books/opds").apply { mkdirs() }
        val target = File(cacheDir, "${ArchiveMapping.uid(source, url)}.epub")
        if (target.isFile && target.length() > 0L) return@withContext target
        val temp = File(target.path + ".tmp")
        try {
            client.newCall(Request.Builder().url(url).build()).execute().use { response ->
                if (!response.isSuccessful) error("OPDS download HTTP ${response.code}")
                response.body?.byteStream()?.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var copied = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            copied += count
                            if (copied > 128L * 1024 * 1024) error("OPDS EPUB exceeds 128 MB limit")
                            output.write(buffer, 0, count)
                        }
                    }
                } ?: error("Empty OPDS acquisition")
            }
            if (!temp.renameTo(target)) error("Cannot cache EPUB")
            target
        } finally {
            temp.delete()
        }
    }
}
