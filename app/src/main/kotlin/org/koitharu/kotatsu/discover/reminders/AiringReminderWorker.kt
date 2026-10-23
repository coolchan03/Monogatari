package org.koitharu.kotatsu.discover.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.Reusable
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.discover.AniListDiscover
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.multi.SearchActivity
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Announces newly aired episodes of the anime followed from Discover's "Airing this week" list. */
@HiltWorker
class AiringReminderWorker @AssistedInject constructor(
	@Assisted context: Context,
	@Assisted params: WorkerParameters,
	private val discover: AniListDiscover,
	private val reminders: AiringReminders,
) : CoroutineWorker(context, params) {

	override suspend fun doWork(): Result {
		val ids = reminders.followedIds()
		if (ids.isEmpty()) {
			WorkManager.getInstance(applicationContext).cancelUniqueWork(WORK_NAME)
			return Result.success()
		}
		val now = System.currentTimeMillis() / 1000
		val aired = try {
			discover.loadFollowedAiring(ids, AiringReminders.windowStart(reminders.lastCheck, now), now)
		} catch (e: CancellationException) {
			throw e
		} catch (_: Exception) {
			return Result.retry()
		}
		val fresh = AiringReminders.newEpisodes(aired, ids.associateWith { reminders.lastNotifiedAiringAt(it) })
		if (fresh.isNotEmpty() && applicationContext.checkNotificationPermission(CHANNEL_ID)) {
			createChannel(applicationContext)
			fresh.forEach { episode ->
				runCatching { showNotification(episode, reminders.title(episode.mediaId) ?: episode.title) }
			}
		}
		// Marked even without notification permission, so granting it later does not replay a backlog.
		fresh.forEach { reminders.markNotified(it) }
		reminders.lastCheck = now
		return Result.success()
	}

	@RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
	private fun showNotification(episode: AniListDiscover.AiredEpisode, title: String) {
		val id = episode.mediaId * 31 + episode.episode
		// (show, episode) pairs can share [id]; the tag and the intent action keep every notification distinct.
		val identity = AniListDiscover.airingKey(episode.mediaId, episode.episode)
		val text = if (episode.episode > 0) {
			applicationContext.getString(R.string.airing_reminder_episode_out, title, episode.episode)
		} else {
			applicationContext.getString(R.string.airing_reminder_new_episode_out, title)
		}
		val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
			.setSmallIcon(R.drawable.ic_stat_suggestion)
			.setContentTitle(text)
			.setContentText(applicationContext.getString(R.string.airing_reminder_tap_to_search))
			.setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
			.setPriority(NotificationCompat.PRIORITY_DEFAULT)
			.setWhen(episode.airingAt * 1000)
			.setShowWhen(true)
			.setAutoCancel(true)
			.setContentIntent(
				PendingIntentCompat.getActivity(
					applicationContext,
					id,
					searchIntent(applicationContext, title).setAction("$TAG:$identity"),
					PendingIntent.FLAG_UPDATE_CURRENT,
					false,
				),
			)
			.build()
		NotificationManagerCompat.from(applicationContext).notify("$TAG:$identity", id, notification)
	}

	@Reusable
	class Scheduler @Inject constructor(
		private val workManager: WorkManager,
	) {

		/** Keeps the periodic check running only while at least one show is followed. */
		fun update(hasFollowed: Boolean) {
			if (!hasFollowed) {
				workManager.cancelUniqueWork(WORK_NAME)
				return
			}
			val constraints = Constraints.Builder()
				.setRequiredNetworkType(NetworkType.CONNECTED)
				.build()
			val request = PeriodicWorkRequestBuilder<AiringReminderWorker>(3, TimeUnit.HOURS)
				.setConstraints(constraints)
				.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
				.build()
			workManager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
		}
	}

	companion object {

		const val CHANNEL_ID = "airing_reminders"
		const val WORK_NAME = "airing_reminders"
		private const val TAG = "airing_reminder"

		fun createChannel(context: Context) {
			val channel = NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
				.setName(context.getString(R.string.airing_reminders_channel))
				.setDescription(context.getString(R.string.airing_reminders_channel_summary))
				.setShowBadge(true)
				.setLightsEnabled(false)
				.build()
			NotificationManagerCompat.from(context).createNotificationChannel(channel)
		}

		/** Same extras as DiscoverFragment.openTypedSearch: a simple search across video sources. */
		fun searchIntent(context: Context, title: String): Intent = Intent(context, SearchActivity::class.java)
			.putExtra(AppRouter.KEY_QUERY, title)
			.putExtra(AppRouter.KEY_KIND, SearchKind.SIMPLE)
			.putExtra(SearchActivity.EXTRA_MEDIA_TYPE, MediaType.VIDEO.name)
			.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
	}
}
