package org.koitharu.kotatsu.builtin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koitharu.kotatsu.core.cache.MemoryContentCache
import org.koitharu.kotatsu.core.media.ContinuousMediaProvider
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.epub.EpubBook
import org.koitharu.kotatsu.core.parser.CachingMangaRepository
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaChapter
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.model.MangaListFilterCapabilities
import org.koitharu.kotatsu.parsers.model.MangaListFilterOptions
import org.koitharu.kotatsu.parsers.model.MangaPage
import org.koitharu.kotatsu.parsers.model.SortOrder
import java.io.File
import java.util.zip.ZipFile

/** Internet Archive source family: discover collections, read EPUB, play audio/video and open CBZ. */
class ArchiveRepository(
    override val source: BuiltinSource,
    cache: MemoryContentCache,
    private val client: OkHttpClient,
    private val context: Context,
) : CachingMangaRepository(cache), ContinuousMediaProvider {
    override val sortOrders = setOf(SortOrder.POPULARITY, SortOrder.UPDATED)
    override var defaultSortOrder = SortOrder.POPULARITY
    override val filterCapabilities = MangaListFilterCapabilities(isSearchSupported = true)
    override suspend fun getFilterOptions() = MangaListFilterOptions()

    override suspend fun getList(offset: Int, order: SortOrder?, filter: MangaListFilter?): List<Manga> {
        val term = filter?.query?.replace(Regex("""[^\p{L}\p{N} ]"""), " ")?.trim().orEmpty()
        val expression = listOfNotNull(
            term.takeIf { it.isNotBlank() }?.let { "($it)" },
            when (source) {
                BuiltinSource.IA_BOOKS ->
                    "mediatype:texts AND language:(English OR eng) AND -collection:inlibrary AND -collection:printdisabled"
                BuiltinSource.IA_VIDEO -> "mediatype:movies"
                BuiltinSource.IA_AUDIO -> "mediatype:audio"
                BuiltinSource.IA_COMICS -> "collection:comics"
                else -> return emptyList()
            },
        ).joinToString(" AND ")
        val sort = if (order == SortOrder.UPDATED) "publicdate desc" else "downloads desc"
        val url = "https://archive.org/advancedsearch.php?q=${ArchiveMapping.encode(expression)}" +
            "&fl[]=identifier&fl[]=title&fl[]=creator&fl[]=subject" +
            "&rows=30&page=${offset.coerceAtLeast(0) / 30 + 1}&output=json&sort[]=${ArchiveMapping.encode(sort)}"
        return ArchiveMapping.mapResults(source, fetch(url))
    }

    override suspend fun getDetailsImpl(manga: Manga): Manga {
        val id = identifier(manga.url)
        val metadata = fetch("https://archive.org/metadata/${ArchiveMapping.encode(id)}")
        val files = metadata.optJSONArray("files") ?: return manga
        val names = (0 until files.length()).mapNotNull { i ->
            files.optJSONObject(i)?.optString("name")?.takeIf { it.isNotBlank() }
        }
        val chapters: List<MangaChapter> = when (source.type) {
            MediaType.BOOK -> {
                val name = names.firstOrNull { it.endsWith(".epub", true) } ?: return manga
                val book = EpubBook.open(download(id, name))
                book.chapters.mapIndexed { index, chapter ->
                    chapter(id, "epub:$name#${chapter.href}", chapter.title, index)
                }
            }
            MediaType.VIDEO, MediaType.AUDIO -> {
                val suffixes = if (source.type == MediaType.VIDEO) listOf(".mp4", ".ogv") else listOf(".mp3")
                names.filter { name -> suffixes.any { name.endsWith(it, true) } }
                    .mapIndexed { index, name -> chapter(id, "play:$name", name, index) }
            }
            MediaType.MANGA -> {
                names.firstOrNull { it.endsWith(".cbz", true) }?.let { listOf(chapter(id, "cbz:$it", manga.title, 0)) }
                    ?: emptyList()
            }
        }
        return manga.copy(chapters = chapters, description = metadata.optJSONObject("metadata")?.optString("description"))
    }

    private fun chapter(id: String, ref: String, title: String, index: Int): MangaChapter {
        val url = "$id#$ref"
        return MangaChapter(
            id = ArchiveMapping.uid(source, url),
            title = title,
            number = (index + 1).toFloat(),
            volume = 0,
            url = url,
            scanlator = null,
            uploadDate = 0L,
            branch = null,
            source = source,
        )
    }

    override suspend fun getPagesImpl(chapter: MangaChapter): List<MangaPage> {
        val ref = chapter.url.substringAfter('#', "")
        if (!ref.startsWith("cbz:")) {
            return listOf(MangaPage(ArchiveMapping.uid(source, chapter.url), chapter.url, null, source))
        }
        val id = identifier(chapter.url)
        val filename = ref.removePrefix("cbz:")
        val cbz = download(id, filename)
        return withContext(Dispatchers.IO) {
            val output = File(context.cacheDir, "archive-pages/${ArchiveMapping.uid(source, chapter.url)}")
            output.mkdirs()
            var extractedBytes = 0L
            ZipFile(cbz).use { zip ->
                zip.entries().asSequence()
                    .filter { !it.isDirectory && it.name.substringAfterLast('.').lowercase() in setOf("jpg","jpeg","png","webp","gif") }
                    .sortedBy { it.name }
                    .take(500)
                    .mapIndexed { index, entry ->
                        val page = File(output, "$index.${entry.name.substringAfterLast('.').lowercase()}")
                        if (page.exists()) {
                            extractedBytes += page.length()
                        } else {
                            val temp = File(output, page.name + ".part")
                            try {
                                zip.getInputStream(entry).use { input ->
                                    temp.outputStream().use { out ->
                                        val buffer = ByteArray(8192)
                                        var size = 0L
                                        while (true) {
                                            val n = input.read(buffer)
                                            if (n < 0) break
                                            size += n
                                            extractedBytes += n
                                            if (size > 30L * 1024 * 1024 || extractedBytes > 256L * 1024 * 1024) {
                                                error("CBZ expands beyond size limit")
                                            }
                                            out.write(buffer, 0, n)
                                        }
                                    }
                                }
                                if (!temp.renameTo(page)) error("Cannot save extracted page")
                            } finally {
                                temp.delete()
                            }
                        }
                        if (extractedBytes > 256L * 1024 * 1024) error("CBZ expands beyond size limit")
                        MangaPage(ArchiveMapping.uid(source, "$index:${chapter.url}"), page.toURI().toString(), null, source)
                    }.toList()
            }
        }
    }

    override suspend fun getRelatedMangaImpl(seed: Manga): List<Manga> = emptyList()
    override suspend fun getPageUrl(page: MangaPage) = page.url

    override suspend fun getChapterHtml(title: String, chapter: MangaChapter): String {
        val ref = chapter.url.substringAfter('#', "")
        if (!ref.startsWith("epub:")) return ""
        val info = ref.removePrefix("epub:")
        val name = info.substringBefore('#')
        val href = info.substringAfter('#', "")
        val book = EpubBook.open(download(identifier(chapter.url), name))
        return book.chapterHtml(href)
    }

    override suspend fun getStreams(chapter: MangaChapter): List<MediaStream> {
        val ref = chapter.url.substringAfter('#', "")
        if (!ref.startsWith("play:")) return emptyList()
        val url = "https://archive.org/download/${ArchiveMapping.encode(identifier(chapter.url))}/" +
            ArchiveMapping.encode(ref.removePrefix("play:"))
        return listOf(MediaStream(url, "Original"))
    }

    private fun identifier(url: String): String =
        url.substringBefore('#').substringAfterLast('/').takeIf { it.isNotBlank() && it != ".." && it != "." }
            ?: error("Invalid archive identifier")

    private suspend fun fetch(url: String): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.9)").build()).execute().use { response ->
            if (!response.isSuccessful) error("Internet Archive HTTP ${response.code}")
            JSONObject(response.body?.string() ?: error("Empty archive response"))
        }
    }

    private suspend fun download(id: String, filename: String): File = withContext(Dispatchers.IO) {
        val cacheKey = ArchiveMapping.uid(source, "$id/$filename")
        val ext = filename.substringAfterLast('.', "bin").take(6).filter(Char::isLetterOrDigit)
        val target = File(context.cacheDir, "archive-files/$cacheKey.$ext")
        if (target.isFile && target.length() > 0L) return@withContext target
        target.parentFile?.mkdirs()
        val url = "https://archive.org/download/${ArchiveMapping.encode(id)}/${ArchiveMapping.encode(filename)}"
        val temp = File(target.path + ".tmp")
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (compatible; Monogatari/0.9)").build()).execute().use { response ->
                if (!response.isSuccessful) error("Archive download HTTP ${response.code}")
                response.body?.byteStream()?.use { input ->
                    temp.outputStream().use { output ->
                        val buffer = ByteArray(16384)
                        var count = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            count += n
                            if (count > 256L * 1024 * 1024) error("Archive file exceeds cache limit")
                            output.write(buffer, 0, n)
                        }
                    }
                } ?: error("Empty Archive response")
            }
            if (!temp.renameTo(target)) error("Could not cache archive")
            target
        } finally {
            temp.delete()
        }
    }
}
