package org.koitharu.kotatsu.videodl

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import org.koitharu.kotatsu.core.media.AudioPlaybackService

/**
 * Hands the current stream to [AudioPlaybackService] (which owns a MediaSession and notification),
 * so it keeps playing as audio after the video screen closes. The service reads the position
 * through the MediaItem, so a URL, title and position are all it needs.
 */
@OptIn(UnstableApi::class)
object BackgroundAudioHandoff {

	private const val TIMEOUT_MS = 15_000L

	/**
	 * [onResult] is called once on the main thread: true when the service started playing, false when it
	 * could not (the caller should then keep its own player running).
	 */
	fun start(
		context: Context,
		url: String,
		mimeType: String?,
		title: String?,
		artist: String?,
		positionMs: Long,
		streamKeys: List<StreamKey> = emptyList(),
		onResult: (Boolean) -> Unit,
	) {
		val token = SessionToken(context, ComponentName(context, AudioPlaybackService::class.java))
		val future = MediaController.Builder(context, token).buildAsync()
		val handler = Handler(Looper.getMainLooper())
		var finished = false
		var controller: MediaController? = null
		var listener: Player.Listener? = null
		fun finish(started: Boolean) {
			if (finished) return
			finished = true
			handler.removeCallbacksAndMessages(null)
			controller?.let { current ->
				listener?.let(current::removeListener)
				if (!started) current.stop()
			}
			MediaController.releaseFuture(future)
			onResult(started)
		}
		future.addListener({
			val connected = runCatching { future.get() }.getOrNull()
			if (connected == null || finished) {
				finish(false)
				return@addListener
			}
			controller = connected
			val watcher = object : Player.Listener {
				override fun onIsPlayingChanged(isPlaying: Boolean) {
					if (isPlaying) finish(true)
				}

				override fun onPlayerError(error: PlaybackException) = finish(false)
			}
			listener = watcher
			connected.addListener(watcher)
			// No picture is shown, so do not decode video in the service.
			connected.trackSelectionParameters = connected.trackSelectionParameters.buildUpon()
				.setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
				.build()
			val item = MediaItem.Builder()
				.setUri(Uri.parse(url))
				.setMimeType(mimeType)
				.setStreamKeys(streamKeys)
				.setMediaMetadata(MediaMetadata.Builder().setTitle(title).setArtist(artist).build())
				.build()
			connected.setMediaItem(item, positionMs.coerceAtLeast(0L))
			connected.prepare()
			connected.play()
			handler.postDelayed({ finish(false) }, TIMEOUT_MS)
		}, ContextCompat.getMainExecutor(context))
	}
}
