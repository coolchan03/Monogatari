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
import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.sourcehealth.SourceActivityLog
import org.koitharu.kotatsu.sourcerepo.domain.SourceRepoRepository
import java.util.concurrent.ConcurrentHashMap
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
    private val activityLog: SourceActivityLog,
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
            typesFromInput(inputData.getStringArray(KEY_TYPES)),
            inputData.getBoolean(KEY_OTHER_LANGUAGES, false),
        )
        val total = entries.size
        val done = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val installedByType = ConcurrentHashMap<JsItemType, AtomicInteger>()
        setProgress(workDataOf(KEY_TOTAL to total, KEY_DONE to 0, KEY_FAILED to 0))
        val semaphore = Semaphore(4)
        coroutineScope {
            entries.map { entry ->
                async {
                    semaphore.withPermit {
                        try {
                            repositories.installJsSource(entry)
                            installedByType.getOrPut(entry.itemType) { AtomicInteger(0) }.incrementAndGet()
                        } catch (cancel: CancellationException) {
                            throw cancel
                        } catch (e: Exception) {
                            failed.incrementAndGet()
                            activityLog.record("Install ${entry.name}", false, e.message ?: e.javaClass.simpleName)
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
            KEY_INSTALLED_ANIME to (installedByType[JsItemType.ANIME]?.get() ?: 0),
            KEY_INSTALLED_NOVEL to (installedByType[JsItemType.NOVEL]?.get() ?: 0),
            KEY_INSTALLED_MANGA to (installedByType[JsItemType.MANGA]?.get() ?: 0),
        ))
    }

    @Reusable
    class Scheduler @Inject constructor(private val workManager: WorkManager) {
        /** A repeated tap does not start a second concurrent mass installation. */
        fun start(
            includeAdult: Boolean,
            types: Set<JsItemType> = JsItemType.entries.toSet(),
            includeOtherLanguages: Boolean = false,
        ) {
            val request = OneTimeWorkRequestBuilder<SourceBootstrapWorker>()
                .setInputData(workDataOf(
                    KEY_ALLOW_ADULT to includeAdult,
                    KEY_TYPES to types.map { it.name }.toTypedArray(),
                    KEY_OTHER_LANGUAGES to includeOtherLanguages,
                ))
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
        const val KEY_TYPES = "types"
        const val KEY_OTHER_LANGUAGES = "other_languages"
        const val KEY_INSTALLED_ANIME = "installed_anime"
        const val KEY_INSTALLED_NOVEL = "installed_novel"
        const val KEY_INSTALLED_MANGA = "installed_manga"

        /** A missing or unreadable list (older work requests) means every type. */
        fun typesFromInput(names: Array<String>?): Set<JsItemType> {
            val parsed = names?.mapNotNull { name -> JsItemType.entries.firstOrNull { it.name == name } }?.toSet()
            return if (parsed.isNullOrEmpty()) JsItemType.entries.toSet() else parsed
        }
    }
}
