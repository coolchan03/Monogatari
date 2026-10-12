package org.koitharu.kotatsu.builtin.newpipe

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayOutputStream
import java.time.Instant

/** YouTube's public Atom feed, used to augment followed channel updates without API keys. */
internal class YouTubeChannelFeed(private val client: OkHttpClient) {
    data class Video(val id: String, val title: String, val publishedAt: Long) {
        val url: String get() = "https://www.youtube.com/watch?v=$id"
    }

    fun fetch(channelId: String): List<Video> {
        if (!validChannelId(channelId)) return emptyList()
        val url = "https://www.youtube.com/feeds/videos.xml?channel_id=$channelId"
        return client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return@use emptyList()
            val stream = response.body?.byteStream() ?: return@use emptyList()
            val bytes = stream.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > 512_000) error("YouTube RSS feed exceeds limit")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            }
            parse(bytes.toString(Charsets.UTF_8))
        }
    }

    companion object {
        private val CHANNEL_ID = Regex("""UC[A-Za-z0-9_-]{22}""")
        private val VIDEO_ID = Regex("""[A-Za-z0-9_-]{11}""")
        fun validChannelId(id: String): Boolean = CHANNEL_ID.matches(id)

        fun videoId(url: String): String? = url.substringAfter("v=", "")
            .substringBefore('&').takeIf { VIDEO_ID.matches(it) }

        fun parse(xml: String): List<Video> {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            return doc.select("entry").mapNotNull { entry ->
                val id = entry.getElementsByTag("yt:videoId").firstOrNull()?.text().orEmpty()
                if (!VIDEO_ID.matches(id)) return@mapNotNull null
                val title = entry.selectFirst("title")?.text().orEmpty().trim()
                if (title.isBlank()) return@mapNotNull null
                val timestamp = runCatching {
                    Instant.parse(entry.selectFirst("published")?.text().orEmpty()).toEpochMilli()
                }.getOrDefault(0L)
                Video(id, title, timestamp)
            }.distinctBy { it.id }.take(20)
        }
    }
}
