package org.koitharu.kotatsu.sourcehealth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.exceptions.CloudFlareException
import org.koitharu.kotatsu.core.model.unwrap
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.sourcerepo.work.SourceRepairWorker
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.sourcescore.domain.ProbeFailureFilter
import javax.inject.Inject
import javax.inject.Singleton

/** Persistent per-parser health; does not upload reading history or disable sources. */
@Singleton
class SourceHealthRecorder @Inject constructor(
    private val db: MangaDatabase,
    @ApplicationContext private val context: Context,
    private val jsStore: JsSourceStore,
    private val repairScheduler: SourceRepairWorker.Scheduler,
    private val retirementTracker: SourceRetirementTracker,
    private val activityLog: SourceActivityLog,
) {
    private val dao get() = db.getSourcesDao()

    private fun isWebsiteSource(source: MangaSource): Boolean = when (source.unwrap()) {
        is MangaParserSource, is JsMangaSource, is BuiltinSource -> true
        else -> false
    }

    private suspend fun ensureIfNotParser(source: MangaSource) {
        if (source.unwrap() !is MangaParserSource) dao.ensureHealthRow(source.name)
    }

    suspend fun success(source: MangaSource) {
        if (!isWebsiteSource(source)) return
        activityLog.record(source.name, true, null)
        ensureIfNotParser(source)
        dao.recordHealthSuccess(source.name, System.currentTimeMillis())
        (source.unwrap() as? JsMangaSource)?.let { jsStore.confirmUpdate(it.id) }
    }

    suspend fun failure(source: MangaSource, error: Throwable) {
        if (error is CancellationException) throw error
        if (!isWebsiteSource(source)) return
        activityLog.record(source.name, false, if (isCloudflare(error)) "Needs a human check (Cloudflare)" else error.message ?: error.javaClass.simpleName)
        if (isCloudflare(error)) return
        val validated = isOnline()
        val recent = if (validated && ProbeFailureFilter.isConnectionLevel(error)) {
            dao.hasRecentSuccessElsewhere(source.name,
                System.currentTimeMillis() - ProbeFailureFilter.RECENT_SUCCESS_WINDOW_MS)
        } else false
        if (ProbeFailureFilter.shouldDiscard(error, validated, recent)) return
        ensureIfNotParser(source)
        dao.recordHealthFailure(source.name)
        val js = source.unwrap() as? JsMangaSource ?: return
        val state = dao.findOne(source.name) ?: return
        val now = System.currentTimeMillis()
        if (SourceHealthPolicy.shouldRollback(state.failCount, state.lastSuccessAt, now,
                jsStore.isFreshUpdate(js.id, now))) {
            if (jsStore.rollback(js.id, now)) {
                dao.clearHealthFailures(source.name)
                return
            }
        }
        if (state.failCount == 3 && !jsStore.isUpdatePaused(js.id)) {
            repairScheduler.repair(js.id)
        }
    }

    suspend fun unhealthyNames(now: Long = System.currentTimeMillis()): Set<String> =
        dao.findUnhealthyNames(SourceHealthPolicy.FAIL_THRESHOLD, now - SourceHealthPolicy.OLD_SUCCESS_MS).toSet() +
            retirementTracker.retiredNames(now)

    private fun isCloudflare(error: Throwable): Boolean {
        var current: Throwable? = error
        repeat(8) {
            if (current is CloudFlareException) return true
            current = current?.cause
        }
        return false
    }

    private fun isOnline(): Boolean = runCatching {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return@runCatching true
        val network = manager.activeNetwork ?: return@runCatching false
        manager.getNetworkCapabilities(network)?.hasCapability(
            NetworkCapabilities.NET_CAPABILITY_VALIDATED
        ) == true
    }.getOrDefault(true)
}
