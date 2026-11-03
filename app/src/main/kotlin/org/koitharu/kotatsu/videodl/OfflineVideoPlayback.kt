package org.koitharu.kotatsu.videodl

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.media3.common.MimeTypes
import androidx.media3.common.StreamKey
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.google.android.material.chip.Chip
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaStream
import org.koitharu.kotatsu.videodl.OfflineStreamPolicy.Protocol

/** Everything the players need to play a saved item without a network. */
@OptIn(UnstableApi::class)
object OfflineVideoPlayback {

	fun mimeFor(protocol: Protocol, original: String?): String? = when (protocol) {
		Protocol.HLS -> MimeTypes.APPLICATION_M3U8
		Protocol.DASH -> MimeTypes.APPLICATION_MPD
		Protocol.PROGRESSIVE -> original
	}

	/** The stream to hand to a player so it reads [entry] from the cache. */
	fun streamOf(entry: OfflineVideoStore.Entry): MediaStream = MediaStream(
		url = entry.cacheUrl ?: entry.streamUrl,
		quality = "Offline",
		audioUrl = entry.audioUrl,
		mimeType = entry.mimeType,
	)

	/** DASH representations that were saved; empty for everything else. */
	fun streamKeys(entry: OfflineVideoStore.Entry): List<StreamKey> =
		entry.streamKeys.mapNotNull(OfflineStreamPolicy.DashKey::decode)
			.map { StreamKey(it.period, it.adaptationSet, it.representation) }

	/** Reads only from the offline cache: it plays with the network off and never goes online. */
	fun cacheOnlyMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
		val factory = CacheDataSource.Factory()
			.setCache(OfflineVideoManager.cache(context))
			.setUpstreamDataSourceFactory(null)
			.setCacheWriteDataSinkFactory(null)
		return DefaultMediaSourceFactory(factory)
	}

	/**
	 * Normal playback that still uses saved data when the URL happens to be cached. It never writes to
	 * the offline cache, so streaming does not fill it.
	 */
	fun readThroughMediaSourceFactory(context: Context): DefaultMediaSourceFactory {
		val factory = CacheDataSource.Factory()
			.setCache(OfflineVideoManager.cache(context))
			.setUpstreamDataSourceFactory(DefaultDataSource.Factory(context))
			.setCacheWriteDataSinkFactory(null)
			.setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
		return DefaultMediaSourceFactory(factory)
	}

	/** Adds a small "Offline" chip to the corner of [playerView]; the chip starts hidden. */
	fun attachChip(playerView: PlayerView): View {
		val dp = playerView.resources.displayMetrics.density
		val chip = Chip(playerView.context).apply {
			setText(R.string.offline_video_chip)
			isClickable = false
			isCheckable = false
			visibility = View.GONE
		}
		val margin = (8 * dp).toInt()
		playerView.overlayFrameLayout?.addView(
			chip,
			FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.WRAP_CONTENT,
				FrameLayout.LayoutParams.WRAP_CONTENT,
				Gravity.TOP or Gravity.END,
			).apply { setMargins(margin, margin, margin, margin) },
		)
		return chip
	}
}
