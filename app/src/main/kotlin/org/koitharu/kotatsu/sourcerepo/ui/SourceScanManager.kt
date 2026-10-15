package org.koitharu.kotatsu.sourcerepo.ui

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs long source sweeps independently of Fragment and dialog lifecycles.
 * Results are journaled after every source so an interrupted sweep can resume
 * without issuing the same 1500+ network requests again.
 *
 * This is a process-lifetime task, not an Android foreground service. If Android
 * kills the process, the user can reopen Source Repositories and resume.
 */
@Singleton
class SourceScanManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val diagnostics: JsSourceDiagnostics,
) {
    data class Status(
        val running: Boolean = false,
        val mode: String = "all",
        val done: Int = 0,
        val total: Int = 0,
        val finished: Boolean = false,
        val message: String = "",
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val status = MutableStateFlow(Status())
    private var activeJob: Job? = null

    fun hasSavedResults(allSources: Boolean): Boolean =
        checkpoint(if (allSources) "all" else "js").exists()

    @Synchronized
    fun start(allSources: Boolean, restart: Boolean = false): Boolean {
        if (activeJob?.isActive == true) return false
        val mode = if (allSources) "all" else "js"
        val checkpoint = checkpoint(mode)
        if (restart) {
            try {
                checkpoint.clear()
            } catch (error: Exception) {
                status.value = Status(mode = mode, message = "Unable to clear saved results: ${error.message}")
                return false
            }
        }
        val previous = checkpoint.read().mapValues { (_, record) -> record.detail }
        status.value = Status(
            running = true, mode = mode, done = previous.size,
            message = if (previous.isEmpty()) "Preparing website catalog..." else "Resuming saved results...",
        )
        activeJob = scope.launch {
            try {
                if (allSources) {
                    diagnostics.runAll(
                        onProgress = { done, total -> updateProgress(mode, done, total) },
                        completed = previous,
                        onResult = { key, line -> checkpoint.append(SourceSweepCheckpoint.Record(key, line.substringBefore(" |"), line)) },
                    )
                } else {
                    diagnostics.run(
                        onProgress = { done, total -> updateProgress(mode, done, total) },
                        completed = previous,
                        onResult = { key, line -> checkpoint.append(SourceSweepCheckpoint.Record(key, line.substringBefore(" |"), line)) },
                    )
                }
                val finished = status.value
                status.value = finished.copy(
                    running = false, finished = true,
                    message = "Sweep complete. Use Copy report to share the results.",
                )
            } catch (cancel: CancellationException) {
                status.value = status.value.copy(
                    running = false, finished = false,
                    message = "Scan stopped. Completed results are saved; Resume skips them.",
                )
            } catch (error: Exception) {
                status.value = status.value.copy(
                    running = false, finished = false,
                    message = "Scan interrupted: ${error.message.orEmpty().take(160)}. Results are saved.",
                )
            }
        }
        return true
    }

    @Synchronized
    fun stop() {
        activeJob?.cancel()
    }

    @Synchronized
    private fun updateProgress(mode: String, done: Int, total: Int) {
        val latest = maxOf(done, status.value.done)
        status.value = status.value.copy(
            mode = mode, done = latest, total = total,
            message = "Checked $latest of $total sources (up to 4 concurrently). Leaving this screen will not stop the scan.",
        )
    }

    fun report(allSources: Boolean): String {
        val mode = if (allSources) "all" else "js"
        val entries = checkpoint(mode).read().mapValues { (_, record) -> record.detail }
        val counts = entries.values.groupingBy { line ->
            line.substringBefore(" |").takeIf { it in setOf("PASS", "PARTIAL", "FAIL") } ?: "OTHER"
        }.eachCount()
        return buildString {
            appendLine(if (allSources) "Monogatari full source diagnostics — saved results" else "Monogatari JS diagnostics — saved results")
            appendLine("Saved checks=${entries.size} PASS=${counts["PASS"] ?: 0} PARTIAL=${counts["PARTIAL"] ?: 0} FAIL=${counts["FAIL"] ?: 0}")
            appendLine("Incomplete checks are not marked failed. Partial scans can be resumed.")
            val categories = entries.values.filter { it.startsWith("FAIL |") }
                .groupingBy { line ->
                    Regex("""category=([A-Z0-9_]+)""").find(line)?.groupValues?.get(1) ?: "OTHER"
                }.eachCount().entries.sortedByDescending { it.value }
            if (categories.isNotEmpty()) appendLine("Failure categories: " + categories.joinToString { "${it.key}=${it.value}" })
            entries.values.forEach { appendLine(it) }
            appendLine("Website listing tests do not prove reading or video playback.")
        }
    }
    private fun checkpoint(mode: String): SourceSweepCheckpoint =
        SourceSweepCheckpoint(File(context.filesDir, "monogatari-source-sweep-$mode-v1.jsonl"))

}
