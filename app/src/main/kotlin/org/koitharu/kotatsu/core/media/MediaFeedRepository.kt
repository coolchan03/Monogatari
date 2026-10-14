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
        require(validUrl(url)) { "Enter a public HTTPS podcast or video RSS feed URL" }
        val request = Request.Builder().url(url)
            .header("Accept", "application/rss+xml, application/atom+xml, text/xml, application/xml")
            .header("User-Agent", "Monogatari/1.0 (podcast feed)").build()
        val xml = client.newCall(request).execute().use { response ->
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
    companion object {
        fun validUrl(value: String): Boolean {
            val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return false
            return uri.scheme.equals("https", true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                value.length in 9..2048
        }
    }
}
