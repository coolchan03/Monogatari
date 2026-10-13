package org.koitharu.kotatsu.sourcehealth

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.alternatives.ui.SourceReplacementActivity
import org.koitharu.kotatsu.core.model.parcelable.ParcelableManga
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Once a day, finds library titles whose source has stopped working (unhealthy for a week, or removed from its
 * extension repository) and posts one notification that opens the source replacement screen for them.
 * Nothing is moved automatically: picking the replacement stays with the user.
 */
@HiltWorker
class BrokenLibrarySourcesWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val favouritesRepository: FavouritesRepository,
	private val healthRecorder: SourceHealthRecorder,
	private val retirementTracker: SourceRetirementTracker,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		val brokenSources = healthRecorder.unhealthyNames() + retirementTracker.retiredNames()
		if (brokenSources.isEmpty()) {
			rememberNotified(emptySet())
			return Result.success()
		}
		val affected = favouritesRepository.getAllManga()
			.filter { it.source.name in brokenSources }
			.distinctBy { it.id }
		val ids = affected.mapTo(HashSet()) { it.id }
		if (affected.isEmpty() || ids == lastNotified()) {
			// Nothing new since the last reminder: don't nag about the same titles every day.
			if (affected.isEmpty()) rememberNotified(emptySet())
			return Result.success()
		}
		createChannel(applicationContext)
		if (!applicationContext.checkNotificationPermission(CHANNEL_ID)) {
			return Result.success()
		}
		val intent = Intent(applicationContext, SourceReplacementActivity::class.java)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
			.putParcelableArrayListExtra(
				AppRouter.KEY_MANGA_LIST,
				affected.take(MAX_TITLES).mapTo(ArrayList()) { ParcelableManga(it, withDescription = false) },
			)
		val pendingIntent = PendingIntentCompat.getActivity(applicationContext, 0, intent, 0, false)
		val text = applicationContext.resources.getQuantityString(
			R.plurals.broken_library_sources_text,
			affected.size,
			affected.size,
		)
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.stat_notify_error)
			.setContentTitle(applicationContext.getString(R.string.broken_library_sources_title))
			.setContentText(text)
			.setStyle(
				NotificationCompat.BigTextStyle().bigText(
					text + "\n" + affected.take(5).joinToString(", ") { it.title },
				),
			)
			.setContentIntent(pendingIntent)
			.setAutoCancel(true)
			.setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
			.build()
		runCatching { NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification) }
		rememberNotified(ids)
		return Result.success()
	}

	private fun prefs() = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	private fun lastNotified(): Set<Long> =
		prefs().getStringSet(KEY_NOTIFIED, null).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

	private fun rememberNotified(ids: Set<Long>) {
		prefs().edit { putStringSet(KEY_NOTIFIED, ids.mapTo(HashSet()) { it.toString() }) }
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
	) {

		fun schedule() {
			val request = PeriodicWorkRequestBuilder<BrokenLibrarySourcesWorker>(1, TimeUnit.DAYS)
				.setInitialDelay(2, TimeUnit.HOURS)
				.build()
			workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
		}
	}

	companion object {

		const val CHANNEL_ID = "broken_sources"
		private const val NOTIFICATION_ID = 0x5EAD
		private const val MAX_TITLES = 50
		private const val PREFS = "broken_library_sources"
		private const val KEY_NOTIFIED = "notified_ids"
		private const val WORK_NAME = "monogatari_broken_library_sources"

		fun createChannel(context: Context) {
			val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
				.setName(context.getString(R.string.broken_library_sources_channel))
				.setShowBadge(true)
				.build()
			NotificationManagerCompat.from(context).createNotificationChannel(channel)
		}
	}
}
