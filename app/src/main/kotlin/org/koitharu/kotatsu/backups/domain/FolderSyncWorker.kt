package org.koitharu.kotatsu.backups.domain

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Reusable
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.core.prefs.AppSettings
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Six-hour folder polling plus an on-launch catch-up, never enabled by default. */
@HiltWorker
class FolderSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val folderSync: FolderBackupSync,
    private val settings: AppSettings,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!settings.isFolderSyncEnabled) return Result.success()
        val journal = applicationContext.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE)
        return try {
            folderSync.synchronize()
            journal.edit().remove("last_error").apply()
            Result.success()
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (error: Exception) {
            // Never log file URIs, archive contents, or account secrets into diagnostics.
            journal.edit().putString("last_error", error.javaClass.simpleName)
                .putLong("last_error_at", System.currentTimeMillis()).apply()
            Result.retry()
        }
    }

    @Reusable
    class Scheduler @Inject constructor(
        private val manager: WorkManager,
        private val settings: AppSettings,
        @ApplicationContext private val context: Context,
    ) {
        fun schedule() {
            if (!settings.isFolderSyncEnabled || !settings.isPeriodicalBackupEnabled ||
                settings.periodicalBackupDirectory == null) {
                manager.cancelUniqueWork(PERIODIC)
                manager.cancelUniqueWork(ON_LAUNCH)
                return
            }
            val periodic = PeriodicWorkRequestBuilder<FolderSyncWorker>(6, TimeUnit.HOURS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS)
                .build()
            manager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.KEEP, periodic)
            val journal = context.getSharedPreferences(JOURNAL, Context.MODE_PRIVATE)
            val last = journal.getLong("last_checked_at", 0L)
            if (System.currentTimeMillis() - last >= TimeUnit.HOURS.toMillis(6)) {
                manager.enqueueUniqueWork(ON_LAUNCH, ExistingWorkPolicy.KEEP,
                    OneTimeWorkRequestBuilder<FolderSyncWorker>()
                        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.HOURS).build())
            }
        }
    }

    companion object {
        const val JOURNAL = "monogatari_folder_sync_journal"
        private const val PERIODIC = "monogatari_folder_sync_periodic"
        private const val ON_LAUNCH = "monogatari_folder_sync_on_launch"
    }
}