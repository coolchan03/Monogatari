package org.koitharu.kotatsu.sourcerepo.ui

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.MissingJsHostDiagnostics
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.source.JsExtensionProvider
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import javax.inject.Inject

/** Temporary, read-only diagnostics. Only installed JS sources can be executed. */
class JsSourceDiagnostics @Inject constructor(
    private val store: JsSourceStore,
    private val provider: JsExtensionProvider,
    private val repository: SourceRepoRepository,
    private val mangaRepositories: MangaRepository.Factory,
) {
    private val attempted = mutableSetOf<Long>()

    /** Install runnable JS sources in bounded batches or, when explicitly requested, all available sources. */
    suspend fun installBatch(onProgress: (Int, Int) -> Unit, maxSources: Int = 10): String {
        val available = repository.fetchAll().jsSources.map { it.entry }
            .filter { it.isRunnable }.distinctBy { it.id }
        val installed = store.list().map { it.id }.toSet()
        val pending = available.filter { it.id !in installed && it.id !in attempted }
            .take(maxSources.coerceIn(1, 1000))
        val lines = mutableListOf("Monogatari batch install", "Runnable available=${available.size} installed=${installed.size} batch=${pending.size}")
        pending.forEachIndexed { index, source ->
            attempted += source.id
            try {
                withTimeout(30_000) { repository.installJsSource(source) }
                lines += "${source.id} | ${source.name} | installed"
            } catch (timeout: TimeoutCancellationException) { lines += "${source.id} | ${source.name} | timeout" }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { lines += "${source.id} | ${source.name} | ${error.javaClass.simpleName}: ${error.message.orEmpty().take(120)}" }
            onProgress(index + 1, pending.size)
        }
        return lines.joinToString("\n")
    }

    /** Exercises every Kotatsu built-in parser and every installed JS extension.
     *  Uninstalled JS entries are reported separately; executing those requires installation.
     *  Per-source timeouts prevent one dead website from blocking the entire sweep.
     */
    suspend fun runAll(
        onProgress: (Int, Int) -> Unit,
        completed: Map<String, String> = emptyMap(),
        onResult: (String, String) -> Unit = { _, _ -> },
    ): String {
        val builtins = MangaParserSource.entries.toList()
        val installed = store.list()
        val available = repository.fetchAll().jsSources
            .map { it.entry }.filter { it.isRunnable }.distinctBy { it.id }
        val missingJs = available.count { candidate -> installed.none { it.id == candidate.id } }
        val lines = mutableListOf(
            "Monogatari full source diagnostics",
            "Kotatsu built-ins=${builtins.size} installed JS=${installed.size} uninstalled JS=$missingJs",
        )
        var pass = 0
        var partial = 0
        var fail = 0
        builtins.forEachIndexed { index, source ->
            val key = "KOTATSU:${source.name}"
            val line = completed[key] ?: try {
                val repo = mangaRepositories.create(source)
                val popular = withTimeout(20_000) { repo.getList(0, null, null) }
                if (popular.isEmpty()) {
                    "PARTIAL | KOTATSU | ${source.name} | category=EMPTY_RESULTS | popular=0"
                } else {
                    "PASS | KOTATSU | ${source.name} | popular=${popular.size}"
                }
            } catch (timeout: TimeoutCancellationException) {
                "FAIL | KOTATSU | ${source.name} | category=TIMEOUT"
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                val description = "${error.javaClass.simpleName}: ${error.message.orEmpty().replace('\n', ' ').take(160)}"
                "FAIL | KOTATSU | ${source.name} | category=${sourceFailureCategory(description)} | $description"
            }
            when {
                line.startsWith("PASS |") -> pass++
                line.startsWith("PARTIAL |") -> partial++
                else -> fail++
            }
            lines += line
            if (key !in completed) onResult(key, line)
            onProgress(index + 1, builtins.size + installed.size)
        }
        // Reuse the installed-JS diagnostic checks instead of maintaining a second copy.
        val jsReport = run(
            onProgress = { done, _ -> onProgress(builtins.size + done, builtins.size + installed.size) },
            completed = completed,
            onResult = onResult,
        )
        val jsLines = jsReport.lines().filter {
            it.startsWith("PASS |") || it.startsWith("PARTIAL |") || it.startsWith("FAIL |")
        }
        lines += jsLines.map { "JS | $it" }
        val jsSummary = Regex("Summary: PASS=(\\d+) PARTIAL=(\\d+) FAIL=(\\d+)")
            .find(jsReport)
        if (jsSummary != null) {
            pass += jsSummary.groupValues[1].toInt()
            partial += jsSummary.groupValues[2].toInt()
            fail += jsSummary.groupValues[3].toInt()
        }
        lines.add(2, "Summary: PASS=$pass PARTIAL=$partial FAIL=$fail NOT_INSTALLED=$missingJs")
        lines += "Kotatsu probes test the first popular listing only. JS probes sample listing/search/detail/content. Playback is NOT verified."
        lines += "Uninstalled JS sources were counted, not tested. Install them with the All option for full coverage."
        return lines.joinToString("\n")
    }

    suspend fun run(
        onProgress: (Int, Int) -> Unit,
        completed: Map<String, String> = emptyMap(),
        onResult: (String, String) -> Unit = { _, _ -> },
    ): String {
        val sources = store.list()
        val lines = mutableListOf("Monogatari JS diagnostics v2", "Installed JS sources: ${sources.size}")
        var working = 0
        var partial = 0
        var failed = 0
        sources.forEachIndexed { index, source ->
            val key = "JS:${source.id}"
            completed[key]?.let { cached ->
                lines += cached
                when {
                    cached.startsWith("PASS |") -> working++
                    cached.startsWith("FAIL |") -> failed++
                    else -> partial++
                }
                onProgress(index + 1, sources.size)
                return@forEachIndexed
            }
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
            val status = when {
                result.any { it.startsWith("error=") } -> "FAIL"
                result.any { it.startsWith("search_error=") } || result.any { it == "detail=skipped_no_item" } -> "PARTIAL"
                result.any { it == "popular=0" || it == "search=0" || it == "content=0" } -> "PARTIAL"
                result.any { it.startsWith("content=") } -> "PASS"
                else -> "PARTIAL"
            }
            when (status) {
                "PASS" -> working++
                "FAIL" -> failed++
                else -> partial++
            }
            val problem = result.firstOrNull { it.startsWith("error=") || it.startsWith("search_error=") }
            val category = when {
                problem == null && result.any { it == "popular=0" || it == "search=0" } -> "EMPTY_RESULTS"
                problem == null && result.any { it == "content=0" } -> "EMPTY_CONTENT"
                problem == null -> "NONE"
                else -> sourceFailureCategory(problem)
            }
            val line = "$status | ${source.id} | ${source.name.replace('|', '/')} | ${source.itemType} | category=$category | ${result.joinToString(" ")}"
            lines += line
            onResult(key, line)
            onProgress(index + 1, sources.size)
        }
        lines.add(2, "Summary: PASS=$working PARTIAL=$partial FAIL=$failed")
        lines += "PASS means sampled listing/detail/content calls returned without throwing; usable content and playback are not verified."
        lines += "Note: search uses a single sample query; content tests use the first popular item. Video streams are listed but not played."
        val missingHosts = MissingJsHostDiagnostics.snapshot()
        if (missingHosts.isNotEmpty()) {
            lines += "Missing JS host functions (this process, sampled):"
            missingHosts.entries.sortedByDescending { it.value }.take(30).forEach { (name, count) ->
                lines += "missing host function: $name | calls=$count"
            }
        }
        return lines.joinToString("\n")
    }
}
