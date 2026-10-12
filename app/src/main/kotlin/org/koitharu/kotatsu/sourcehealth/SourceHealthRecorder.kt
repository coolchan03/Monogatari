package org.koitharu.kotatsu.sourcehealth

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.exceptions.CloudFlareException
import org.koitharu.kotatsu.core.model.unwrap
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
) {
    private val dao get() = db.getSourcesDao()

    suspend fun success(source: MangaSource) {
        if (source.unwrap() !is MangaParserSource) return
        dao.recordHealthSuccess(source.name, System.currentTimeMillis())
    }

    suspend fun failure(source: MangaSource, error: Throwable) {
        if (source.unwrap() !is MangaParserSource) return
        if (error is CancellationException) throw error
        if (isCloudflare(error) || !isOnline() || ProbeFailureFilter.isConnectionLevel(error)) return
        dao.recordHealthFailure(source.name)
    }

    suspend fun unhealthyNames(now: Long = System.currentTimeMillis()): Set<String> =
        dao.findUnhealthyNames(SourceHealthPolicy.FAIL_THRESHOLD, now - SourceHealthPolicy.OLD_SUCCESS_MS).toSet()

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
