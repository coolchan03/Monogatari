package org.koitharu.kotatsu.backups.domain

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.db.MangaDatabase
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Checks once a day whether the library has gone too long without a backup and, if so, posts one quiet
 * notification (at most once per chosen interval). Does nothing while the reminder is Off or the library is empty.
 */
@HiltWorker
class BackupReminderWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val database: MangaDatabase,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		val context = applicationContext
		val now = System.currentTimeMillis()
		val interval = BackupStatus.reminderInterval(context)
		if (interval == BackupReminderInterval.OFF) {
			return Result.success()
		}
		val lastBackupAt = BackupStatus.lastBackupAt(context)
		val trackingSince = BackupStatus.trackingSince(context, now)
		val lastReminderAt = BackupStatus.lastReminderAt(context)
		// Cheap pre-check first so the library is only queried when a reminder could actually be due.
		if (!BackupReminderRules.isDue(interval, lastBackupAt, trackingSince, lastReminderAt, true, now)) {
			return Result.success()
		}
		val hasLibrary = database.getHistoryDao().getCount() > 0 ||
			database.getFavouritesDao().findLast(1).isNotEmpty()
		if (!hasLibrary) {
			return Result.success()
		}
		createChannel(context)
		if (!context.checkNotificationPermission(CHANNEL_ID)) {
			return Result.success()
		}
		val text = if (lastBackupAt > 0L) {
			val days = BackupReminderRules.daysSince(lastBackupAt, now)
			context.resources.getQuantityString(R.plurals.backup_reminder_text, days, days)
		} else {
			context.getString(R.string.backup_reminder_text_never)
		}
		val pendingIntent = PendingIntentCompat.getActivity(
			context,
			0,
			AppRouter.historySettingsIntent(context),
			0,
			false,
		)
		// Same screen, but it goes straight to the "save backup" file picker.
		val backUpNowIntent = PendingIntentCompat.getActivity(
			context,
			1,
			AppRouter.historySettingsIntent(context).putExtra(EXTRA_BACKUP_NOW, true),
			0,
			false,
		)
		val notification = NotificationCompat.Builder(context, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.stat_sys_upload)
			.setContentTitle(context.getString(R.string.backup_reminder_title))
			.setContentText(text)
			.setStyle(NotificationCompat.BigTextStyle().bigText(text))
			.setContentIntent(pendingIntent)
			.addAction(0, context.getString(R.string.backup_reminder_action_back_up), backUpNowIntent)
			.setAutoCancel(true)
			.setSilent(true)
			.setPriority(NotificationCompat.PRIORITY_LOW)
			.setCategory(NotificationCompat.CATEGORY_REMINDER)
			.build()
		val posted = runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
		if (posted.isSuccess) {
			BackupStatus.markReminded(context, now)
		}
		return Result.success()
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
		@ApplicationContext private val context: Context,
	) {

		/** Enqueues the daily check while the reminder is on, cancels it while it is Off. Safe to call repeatedly. */
		fun schedule() {
			if (BackupStatus.reminderInterval(context) == BackupReminderInterval.OFF) {
				workManager.cancelUniqueWork(WORK_NAME)
				return
			}
			val request = PeriodicWorkRequestBuilder<BackupReminderWorker>(1, TimeUnit.DAYS)
				.setInitialDelay(6, TimeUnit.HOURS)
				.setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
				.build()
			workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
		}
	}

	companion object {

		const val CHANNEL_ID = "backup_reminders"

		/** Boolean extra on the settings intent: open the manual backup file picker straight away. */
		const val EXTRA_BACKUP_NOW = "org.koitharu.kotatsu.extra.BACKUP_NOW"
		private const val NOTIFICATION_ID = 0xBAC0
		private const val WORK_NAME = "monogatari_backup_reminder"

		fun createChannel(context: Context) {
			val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
				.setName(context.getString(R.string.backup_reminder_channel))
				.setShowBadge(false)
				.setVibrationEnabled(false)
				.setSound(null, null)
				.setLightsEnabled(false)
				.build()
			NotificationManagerCompat.from(context).createNotificationChannel(channel)
		}
	}
}
