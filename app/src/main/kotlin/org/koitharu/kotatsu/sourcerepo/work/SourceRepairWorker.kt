package org.koitharu.kotatsu.sourcerepo.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.sourcerepo.domain.PluginState
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Immediate update check for one failing installed extension only.
 * This is not an arbitrary automatic installation of new, unreviewed sources.
 */
@HiltWorker
class SourceRepairWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repos: SourceRepoRepository,
    private val installed: JsSourceStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getLong(KEY_ID, -1L)
        if (id < 0 || installed.installedVersion(id) == null || installed.isUpdatePaused(id)) {
            return Result.success()
        }
        val index = try {
            repos.fetchAll()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return if (runAttemptCount >= 2) Result.failure() else Result.retry()
        }
        val replacement = index.jsSources.firstOrNull {
            it.entry.id == id && it.state == PluginState.UPDATE_AVAILABLE
        } ?: return if (index.errors.isNotEmpty()) {
            if (runAttemptCount >= 2) Result.failure() else Result.retry()
        } else Result.success()

        return try {
            if (installed.installedVersion(id) != null && !installed.isUpdatePaused(id)) {
                repos.installJsSource(replacement.entry)
            }
            Result.success()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            if (runAttemptCount >= 2) Result.failure() else Result.retry()
        }
    }

    @Reusable
    class Scheduler @Inject constructor(private val manager: WorkManager) {
        fun repair(id: Long) {
            val request = OneTimeWorkRequestBuilder<SourceRepairWorker>()
                .setInputData(workDataOf(KEY_ID to id))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            manager.enqueueUniqueWork("monogatari_js_repair_$id", ExistingWorkPolicy.KEEP, request)
        }
    }

    companion object {
        const val KEY_ID = "source_id"
    }
}
