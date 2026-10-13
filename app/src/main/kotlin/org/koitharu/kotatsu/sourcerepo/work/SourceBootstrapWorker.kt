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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koitharu.kotatsu.core.model.unwrap
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/** Install only user-approved JS websites; WorkManager survives app/process restarts. */
@HiltWorker
class SourceBootstrapWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repositories: SourceRepoRepository,
    private val installedStore: JsSourceStore,
    private val mangaSources: MangaSourcesRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val index = try {
            repositories.fetchAll()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            return Result.retry()
        }
        if (index.jsSources.isEmpty() && index.errors.isNotEmpty()) return Result.retry()
        val aliases = mangaSources.getEnabledSources()
            .map { it.unwrap() }
            .filterIsInstance<MangaParserSource>()
            .flatMap { listOf(it.name, it.title) }.toSet()
        val entries = SourceBootstrapPlanner.plan(
            index.jsSources.map { it.entry },
            installedStore.list(),
            aliases,
            inputData.getBoolean(KEY_ALLOW_ADULT, false),
        )
        val total = entries.size
        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)
        setProgress(workDataOf(KEY_TOTAL to total, KEY_DONE to 0, KEY_FAILED to 0))
        val semaphore = Semaphore(4)
        coroutineScope {
            entries.map { entry ->
                async {
                    semaphore.withPermit {
                        try {
                            repositories.installJsSource(entry)
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (_: Exception) {
                            failed.incrementAndGet()
                        } finally {
                            setProgress(workDataOf(
                                KEY_TOTAL to total,
                                KEY_DONE to done.incrementAndGet(),
                                KEY_FAILED to failed.get(),
                            ))
                        }
                    }
                }
            }.awaitAll()
        }
        return Result.success(workDataOf(
            KEY_TOTAL to total,
            KEY_DONE to done.get(),
            KEY_FAILED to failed.get(),
        ))
    }

    @Reusable
    class Scheduler @Inject constructor(private val workManager: WorkManager) {
        /** A repeated tap does not start a second concurrent mass installation. */
        fun start(includeAdult: Boolean) {
            val request = OneTimeWorkRequestBuilder<SourceBootstrapWorker>()
                .setInputData(workDataOf(KEY_ALLOW_ADULT to includeAdult))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            workManager.enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, request)
        }
    }

    companion object {
        const val NAME = "monogatari_js_initial_sources"
        const val KEY_ALLOW_ADULT = "allow_adult"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_FAILED = "failed"
    }
}
