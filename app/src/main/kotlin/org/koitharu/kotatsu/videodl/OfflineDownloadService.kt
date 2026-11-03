package org.koitharu.kotatsu.videodl

import android.app.Notification
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import org.koitharu.kotatsu.R

/**
 * Foreground service that keeps Media3 offline downloads running, with a progress notification
 * that can pause/resume everything or cancel the download it shows.
 */
@OptIn(UnstableApi::class)
class OfflineDownloadService : DownloadService(
	NOTIFICATION_ID,
	DownloadService.DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
	CHANNEL_ID,
	R.string.offline_video_channel_name,
	R.string.offline_video_channel_description,
) {

	override fun getDownloadManager(): DownloadManager = OfflineVideoManager.manager(this)

	override fun getScheduler(): Scheduler? = null

	override fun getForegroundNotification(downloads: List<Download>, notMetRequirements: Int): Notification {
		val manager = getDownloadManager()
		val active = downloads.filter {
			it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED ||
				it.state == Download.STATE_RESTARTING || it.state == Download.STATE_STOPPED
		}
		if (active.isNotEmpty() && OfflineVideoManager.isFull(this)) {
			// Size cap reached while downloading: stop and discard what is still running.
			active.forEach { manager.removeDownload(it.request.id) }
		}
		val first = active.firstOrNull()
		val title = first?.let { OfflineVideoStore.get(this, OfflineVideoStore.entryIdOf(it.request.id))?.title }
			?: getString(R.string.offline_video_channel_name)
		val percents = active.map { it.percentDownloaded }
		val indeterminate = percents.isEmpty() || percents.any { it < 0f }
		val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		val builder = NotificationCompat.Builder(this, CHANNEL_ID)
			.setSmallIcon(android.R.drawable.stat_sys_download)
			.setContentTitle(title)
			.setContentText(
				when {
					manager.downloadsPaused -> getString(R.string.offline_video_paused)
					notMetRequirements != 0 -> getString(R.string.offline_video_waiting_network)
					else -> getString(R.string.offline_video_downloading)
				},
			)
			.setOngoing(true)
			.setOnlyAlertOnce(true)
			.setProgress(100, if (indeterminate) 0 else percents.average().toInt(), indeterminate)
		val toggle = if (manager.downloadsPaused) {
			DownloadService.buildResumeDownloadsIntent(this, javaClass, false)
		} else {
			DownloadService.buildPauseDownloadsIntent(this, javaClass, false)
		}
		builder.addAction(
			0,
			getString(if (manager.downloadsPaused) R.string.resume else R.string.pause),
			PendingIntent.getService(this, REQUEST_TOGGLE, toggle, flags),
		)
		if (first != null) {
			val cancel = DownloadService.buildRemoveDownloadIntent(this, javaClass, first.request.id, false)
			builder.addAction(
				0,
				getString(android.R.string.cancel),
				PendingIntent.getService(this, REQUEST_CANCEL, cancel, flags),
			)
		}
		return builder.build()
	}

	private companion object {
		const val NOTIFICATION_ID = 0x4D4F5601
		const val CHANNEL_ID = "monogatari_offline_video"
		const val REQUEST_TOGGLE = 1
		const val REQUEST_CANCEL = 2
	}
}
