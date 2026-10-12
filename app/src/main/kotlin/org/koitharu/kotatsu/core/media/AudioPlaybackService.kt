package org.koitharu.kotatsu.core.media

import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/** Dedicated player owner so audio can continue after the reader goes into background. */
class AudioPlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private val checkpoint = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, 15_000L)
        }
    }

    private fun savePosition() {
        val player = session?.player ?: return
        val url = player.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        AudioResumeStore.write(this, url, player.currentPosition.coerceAtLeast(0L))
    }

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true,
            ).build()
        session = MediaSession.Builder(this, player).build()
        handler.postDelayed(checkpoint, 15_000L)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        handler.removeCallbacks(checkpoint)
        savePosition()
        val player = session?.player
        session?.release()
        player?.release()
        session = null
        super.onDestroy()
    }
}
