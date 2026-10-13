package org.koitharu.kotatsu.sourcerepo.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.sourcerepo.domain.PluginState
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Update installed JS sources only; untrusted new websites still require first-run consent. */
@HiltWorker
class SourceUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repos: SourceRepoRepository,
    private val sources: JsSourceStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val indexes = try {
            repos.fetchAll()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return Result.retry()
        }
        if (indexes.errors.isNotEmpty() && indexes.jsSources.isEmpty() && indexes.plugins.isEmpty()) {
            return Result.retry()
        }
        val pending = indexes.jsSources.filter { item ->
            item.state == PluginState.UPDATE_AVAILABLE &&
                sources.installedVersion(item.entry.id) != null &&
                !sources.isUpdatePaused(item.entry.id)
        }
        val semaphore = Semaphore(4)
        val success = coroutineScope {
            pending.map { item ->
                async {
                    semaphore.withPermit {
                        try {
                            repos.installJsSource(item.entry)
                            1
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            0 // One bad extension must not prevent other good updates.
                        }
                    }
                }
            }.awaitAll().sum()
        }
        val now = System.currentTimeMillis()
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("last_check", now)
            .putLong("last_updated_day", now / 86_400_000L)
            .putInt("updates_today", todayCount(now) + success)
            .apply()
        return Result.success()
    }

    private fun todayCount(now: Long): Int {
        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (prefs.getLong("last_updated_day", -1) == now / 86_400_000L) {
            prefs.getInt("updates_today", 0)
        } else 0
    }

    @Reusable
    class Scheduler @Inject constructor(
        private val workManager: WorkManager,
        private val context: Context,
    ) {
        fun schedule() {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val periodic = PeriodicWorkRequestBuilder<SourceUpdateWorker>(12, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
            val last = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong("last_check", 0L)
            if (System.currentTimeMillis() - last >= 6L * 60 * 60 * 1000) {
                val initial = OneTimeWorkRequestBuilder<SourceUpdateWorker>()
                    .setConstraints(constraints)
                    .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                    .build()
                workManager.enqueueUniqueWork(ONE_SHOT, ExistingWorkPolicy.KEEP, initial)
            }
        }
    }

    companion object {
        const val PREFS = "monogatari_js_source_updates"
        private const val PERIODIC = "monogatari_js_update_periodic"
        private const val ONE_SHOT = "monogatari_js_update_startup"
    }
}
