package org.koitharu.kotatsu.videodl

import android.content.Context
import android.content.Intent
import android.text.format.Formatter
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaFeedParser
import org.koitharu.kotatsu.core.media.MediaFeedPlayerActivity
import org.koitharu.kotatsu.core.media.MediaType

/** Helpers for showing saved offline videos in the Library ("On this device"). */
object OfflineVideoShelf {

	fun mediaType(entry: OfflineVideoStore.Entry): MediaType =
		if (entry.mediaType == MediaType.AUDIO.name) MediaType.AUDIO else MediaType.VIDEO

	/** One-line detail such as "Downloaded video · 120 MB · SourceName". */
	fun detail(context: Context, entry: OfflineVideoStore.Entry): String {
		val kind = context.getString(
			if (mediaType(entry) == MediaType.AUDIO) R.string.offline_video_detail_audio else R.string.offline_video_detail_video,
		)
		val parts = mutableListOf(kind)
		if (entry.sizeBytes > 0) parts += Formatter.formatShortFileSize(context, entry.sizeBytes)
		if (entry.sourceName.isNotBlank()) parts += entry.sourceName
		return parts.joinToString(" · ")
	}

	/** Opens the in-app player on the saved copy; it plays with no network. */
	fun playerIntent(context: Context, entry: OfflineVideoStore.Entry): Intent =
		Intent(context, MediaFeedPlayerActivity::class.java)
			.putExtra(MediaFeedPlayerActivity.EXTRA_OFFLINE_ID, entry.id)
			.putExtra(MediaFeedPlayerActivity.EXTRA_TITLE, entry.title)
			.putExtra(
				MediaFeedPlayerActivity.EXTRA_KIND,
				if (mediaType(entry) == MediaType.AUDIO) MediaFeedParser.Kind.AUDIO.name else MediaFeedParser.Kind.VIDEO.name,
			)
}
