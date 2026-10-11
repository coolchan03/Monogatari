package org.koitharu.kotatsu.sourcerepo.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.source.JsExtensionProvider
import javax.inject.Inject

/** Temporary, read-only diagnostics. Only installed JS sources can be executed. */
class JsSourceDiagnostics @Inject constructor(
    private val store: JsSourceStore,
    private val provider: JsExtensionProvider,
) {
    suspend fun run(onProgress: (Int, Int) -> Unit): String {
        val sources = store.list()
        val lines = mutableListOf("Monogatari JS diagnostics v1", "Installed JS sources: ${sources.size}")
        sources.forEachIndexed { index, source ->
            val result = mutableListOf<String>()
            try {
                val extension = withTimeout(20_000) { provider.get(source.id) }
                val popular = withTimeout(20_000) { extension.getPopular(1) }
                result += "popular=${popular.list.size}"
                try {
                    val search = withTimeout(20_000) { extension.search("love", 1) }
                    result += "search=${search.list.size}"
                } catch (timeout: TimeoutCancellationException) { result += "search_error=Timeout" }
                catch (cancel: CancellationException) { throw cancel }
                catch (error: Exception) { result += "search_error=${error.javaClass.simpleName}" }
                val first = popular.list.firstOrNull()
                if (first?.link != null) {
                    val detail = withTimeout(20_000) { extension.getDetail(first.link) }
                    result += "detail=ok chapters=${detail.chapters.size}"
                    val chapter = detail.chapters.firstOrNull()
                    if (chapter != null) {
                        val count = withTimeout(20_000) {
                            when (source.itemType) {
                                JsItemType.MANGA -> extension.getPageList(chapter.url).size
                                JsItemType.ANIME -> extension.getVideoList(chapter.url).size
                                JsItemType.NOVEL -> { extension.getHtmlContent(chapter.name, chapter.url); 1 }
                            }
                        }
                        result += "content=$count"
                    }
                } else result += "detail=skipped_no_item"
            } catch (timeout: TimeoutCancellationException) { result += "error=Timeout" }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                result += "error=${error.javaClass.simpleName}:${error.message.orEmpty().replace('\n', ' ').take(160)}"
            }
            lines += "${source.id} | ${source.name.replace('|', '/')} | ${source.itemType} | ${result.joinToString(" ")}"
            onProgress(index + 1, sources.size)
        }
        lines += "Note: search uses a single sample query; content tests use the first popular item. Video streams are listed but not played."
        return lines.joinToString("\n")
    }
}
