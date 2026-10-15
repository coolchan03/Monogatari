package org.koitharu.kotatsu.core.media

import androidx.media3.common.MimeTypes
import java.net.URI
import java.util.Locale

/** URLs and chapter positions allowed in the in-app RSS video/podcast player. */
internal object MediaFeedPlaybackSpec {
    fun validStream(url: String?): Boolean =
        url != null && MediaFeedRepository.validUrl(url) &&
            runCatching {
                val uri = URI(url)
                uri.fragment == null && (uri.port == -1 || uri.port == 443)
            }.getOrDefault(false)

    /** Only attach actual subtitle sidecars, not HTML transcripts or arbitrary downloads. */
    fun subtitleMimeType(url: String?): String? {
        if (!validStream(url)) return null
        val extension = runCatching { URI(url).path }.getOrNull()
            ?.substringAfterLast('.', "")?.lowercase(Locale.ROOT) ?: return null
        return when (extension) {
            "vtt" -> MimeTypes.TEXT_VTT
            "srt" -> MimeTypes.APPLICATION_SUBRIP
            "ttml" -> MimeTypes.APPLICATION_TTML
            "ssa", "ass" -> MimeTypes.TEXT_SSA
            else -> null
        }
    }

    fun chapterPositionMs(seconds: Int): Long = seconds.coerceAtLeast(0).toLong() * 1000L
}
