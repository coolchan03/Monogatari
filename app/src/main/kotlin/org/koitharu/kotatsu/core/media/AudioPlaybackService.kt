package org.koitharu.kotatsu.core.media

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import org.koitharu.kotatsu.videodl.OfflineVideoPlayback
import org.koitharu.kotatsu.videodl.PlaybackTimeReporter

/** Dedicated player owner so audio can continue after the reader goes into background. */
@OptIn(UnstableApi::class)
class AudioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val listenTime by lazy { PlaybackTimeReporter(this, PlaybackTimeReporter.AUDIO) }
    private val checkpoint = object : Runnable {
        override fun run() {
            // Nothing changes while paused, so do not rewrite the stored position.
            if (session?.player?.isPlaying == true) savePosition()
            handler.postDelayed(this, 15_000L)
        }
    }

    private fun savePosition() {
        val player = session?.player ?: return
        val url = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        AudioResumeStore.write(this, url, resumePositionMs(player.currentPosition, player.duration.coerceAtLeast(0L)))
    }

    override fun onCreate() {
        super.onCreate()
        val builder = ExoPlayer.Builder(this)
        // Saved offline videos/audio play from the offline cache; anything else streams as before.
        runCatching { OfflineVideoPlayback.readThroughMediaSourceFactory(this) }
            .getOrNull()?.let(builder::setMediaSourceFactory)
        val player = builder
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            ).setHandleAudioBecomingNoisy(true).build()
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) = listenTime.setPlaying(isPlaying)
        })
        session = MediaSession.Builder(this, player).build()
        handler.postDelayed(checkpoint, 15_000L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        handler.removeCallbacks(checkpoint)
        listenTime.stop()
        savePosition()
        val player = session?.player
        session?.release()
        player?.release()
        session = null
        super.onDestroy()
    }
}
