package org.koitharu.kotatsu.core.media

import org.json.JSONArray
import org.json.JSONObject

/** User-selected episodes, persisted locally; nothing is fetched or played in the background. */
internal object MediaFeedPlaylist {
    private const val MAX_EPISODES = 200
    private const val MAX_JSON_CHARS = 800_000

    fun id(episode: MediaFeedParser.Episode): String? = episode.mediaUrl ?: episode.pageUrl

    fun contains(items: List<MediaFeedParser.Episode>, episode: MediaFeedParser.Episode): Boolean =
        id(episode)?.let { identity -> items.any { id(it) == identity } } == true

    fun add(items: List<MediaFeedParser.Episode>, episode: MediaFeedParser.Episode):
        List<MediaFeedParser.Episode> =
        if (id(episode) == null) items else (listOf(episode) + remove(items, episode)).take(MAX_EPISODES)

    fun remove(items: List<MediaFeedParser.Episode>, episode: MediaFeedParser.Episode):
        List<MediaFeedParser.Episode> = items.filterNot { id(it) == id(episode) }

    fun encode(items: List<MediaFeedParser.Episode>): String {
        val array = JSONArray()
        items.take(MAX_EPISODES).forEach { episode ->
            array.put(JSONObject().apply {
                put("title", episode.title.take(220))
                put("page", episode.pageUrl.orEmpty())
                put("media", episode.mediaUrl.orEmpty())
                put("mime", episode.mimeType.orEmpty().take(100))
                put("published", episode.published.take(95))
                put("cover", episode.coverUrl.orEmpty())
                put("kind", episode.kind.name)
                put("duration", episode.durationSeconds ?: -1)
                put("chapters", episode.chaptersUrl.orEmpty())
                put("transcript", episode.transcriptUrl.orEmpty())
            })
        }
        return array.toString()
    }

    fun decode(json: String?): List<MediaFeedParser.Episode> {
        if (json.isNullOrBlank() || json.length > MAX_JSON_CHARS) return emptyList()
        val array = runCatching { JSONArray(json) }.getOrNull() ?: return emptyList()
        fun https(value: String): String? = value.takeIf { MediaFeedRepository.validUrl(it) }
        return (0 until minOf(array.length(), MAX_EPISODES)).mapNotNull { i ->
            val item = array.optJSONObject(i) ?: return@mapNotNull null
            val title = item.optString("title").trim().take(220).takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            val page = https(item.optString("page"))
            val media = https(item.optString("media"))
            if (page == null && media == null) return@mapNotNull null
            MediaFeedParser.Episode(
                title = title, pageUrl = page, mediaUrl = media,
                mimeType = item.optString("mime").take(100).takeIf { it.isNotBlank() },
                published = item.optString("published").take(95),
                coverUrl = https(item.optString("cover")),
                kind = MediaFeedParser.Kind.entries.firstOrNull {
                    it.name == item.optString("kind")
                } ?: MediaFeedParser.Kind.AUDIO,
                durationSeconds = item.optInt("duration", -1).takeIf { it > 0 },
                chaptersUrl = https(item.optString("chapters")),
                transcriptUrl = https(item.optString("transcript")),
            )
        }.distinctBy(::id)
    }
}
