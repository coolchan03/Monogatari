package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.network.BaseHttpClient
import java.net.URI
import javax.inject.Inject

/** HTTPS-only, bounded, cancellable feed retrieval via the app's existing HTTP client. */
class MediaFeedRepository @Inject constructor(@BaseHttpClient private val client: OkHttpClient) {
    suspend fun read(url: String): List<MediaFeedParser.Episode> = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        require(validUrl(normalized)) { "Enter a public HTTPS podcast or video RSS feed URL" }
        val request = Request.Builder().url(normalized)
            .header("Accept", "application/rss+xml, application/atom+xml, text/xml, application/xml")
            .header("User-Agent", "Monogatari/1.0 (podcast feed)").build()
        val xml = client.newCall(request).execute().use { response ->
            require(response.request.url.scheme == "https") { "Feed redirected to an insecure URL" }
            require(response.isSuccessful) { "Feed HTTP ${response.code}" }
            val stream = response.body?.byteStream() ?: error("Empty feed")
            stream.use { input ->
                val out = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    require(out.size() + n <= 1_000_000) { "Feed exceeds 1 MB limit" }
                    out.write(chunk, 0, n)
                }
                out.toString("UTF-8")
            }
        }
        MediaFeedParser.parse(xml)
    }
    /** Fetches a Podcasting 2.0 chapters file (small, HTTPS only). */
    suspend fun readChapters(url: String): List<MediaFeedParser.Chapter> = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        require(validUrl(normalized)) { "Chapters must come from an HTTPS address" }
        val request = Request.Builder().url(normalized).header("User-Agent", "Monogatari/1.0 (podcast chapters)").build()
        val text = client.newCall(request).execute().use { response ->
            require(response.request.url.scheme == "https") { "Chapters redirected to an insecure URL" }
            require(response.isSuccessful) { "Chapters HTTP ${response.code}" }
            val source = response.body?.source() ?: error("Empty chapters file")
            source.request(200_001)
            require(source.buffer.size <= 200_000) { "Chapters file too large" }
            source.buffer.readUtf8()
        }
        MediaFeedParser.parseChapters(text)
    }
    companion object {
        fun validUrl(value: String): Boolean {
            val normalized = value.trim()
            val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
            return uri.scheme.equals("https", true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                normalized.length in 9..2048
        }
    }
}
