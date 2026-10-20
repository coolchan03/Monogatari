package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.koitharu.kotatsu.core.network.BaseHttpClient
import org.koitharu.kotatsu.core.util.FeedXml
import java.net.URI
import javax.inject.Inject

/** HTTPS-only, bounded, cancellable feed retrieval via the app's existing HTTP client. */
class MediaFeedRepository @Inject constructor(@BaseHttpClient private val client: OkHttpClient) {
    /** Podcast publisher feeds can exceed 1 MB; the verified built-in feeds require up to 1.7 MB. */
    private val feedClient: OkHttpClient by lazy { client.newBuilder().followSslRedirects(false).build() }
    /** Redirects are followed manually so every hop can be checked (HTTPS only, never downgraded). */
    private val documentClient: OkHttpClient by lazy {
        client.newBuilder().followRedirects(false).followSslRedirects(false).build()
    }
    suspend fun read(url: String): List<MediaFeedParser.Episode> {
        val normalized = url.trim()
        require(validUrl(normalized)) { "Enter a public HTTPS podcast or video RSS feed URL" }
        val xml = fetchFeedText(normalized, MAX_FEED_BYTES)
        return withContext(Dispatchers.Default) { MediaFeedParser.parse(xml) }
    }

    /**
     * Downloads an RSS/Atom document with a browser-like User-Agent (CDNs refuse unknown clients),
     * following up to [MAX_REDIRECTS] redirects that stay on HTTPS and pass [allowed]. The body is limited
     * to [maxBytes] and decoded with the charset from Content-Type, the XML prolog, or UTF-8.
     */
    suspend fun fetchFeedText(
        url: String,
        maxBytes: Int,
        allowed: (String) -> Boolean = ::validUrl,
    ): String = withContext(Dispatchers.IO) {
        var target = url.trim()
        for (hop in 0..MAX_REDIRECTS) {
            require(allowed(target)) { "Feed must use a public HTTPS address" }
            val request = Request.Builder().url(target)
                .header("Accept", FEED_ACCEPT)
                .header("User-Agent", FEED_USER_AGENT).build()
            val step: Pair<String?, String?> = documentClient.newCall(request).execute().use { response ->
                if (response.isRedirect) {
                    val next = response.header("Location")?.let { response.request.url.resolve(it) }
                    requireNotNull(next) { "Feed redirect has no destination" }
                    require(next.isHttps) { "Feed redirected to an insecure URL" }
                    next.toString() to null
                } else {
                    require(response.isSuccessful) { "Feed HTTP ${response.code}" }
                    val bytes = response.body.byteStream().use { input ->
                        val out = java.io.ByteArrayOutputStream()
                        val chunk = ByteArray(8192)
                        while (true) {
                            val n = input.read(chunk)
                            if (n < 0) break
                            require(out.size() + n <= maxBytes) { "Feed exceeds ${maxBytes / 1_000_000} MB limit" }
                            out.write(chunk, 0, n)
                        }
                        out.toByteArray()
                    }
                    null to FeedXml.decode(bytes, response.header("Content-Type"))
                }
            }
            step.second?.let { return@withContext it }
            target = checkNotNull(step.first)
        }
        throw IllegalArgumentException("Feed redirected too many times")
    }
    /** Fetches a Podcasting 2.0 chapters file (small, HTTPS only). */
    suspend fun readChapters(url: String): List<MediaFeedParser.Chapter> = withContext(Dispatchers.IO) {
        val normalized = url.trim()
        require(validUrl(normalized)) { "Chapters must come from an HTTPS address" }
        val request = Request.Builder().url(normalized).header("User-Agent", "Monogatari/1.0 (podcast chapters)").build()
        val text = feedClient.newCall(request).execute().use { response ->
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
        const val MAX_FEED_BYTES = 4_000_000
        const val MAX_REDIRECTS = 5
        const val FEED_ACCEPT = "application/rss+xml, application/atom+xml, application/xml, text/xml, */*"
        /** A current mobile Chrome identity; several publishers' CDNs reject unknown clients with 403. */
        const val FEED_USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/138.0.0.0 Mobile Safari/537.36"
        fun validUrl(value: String): Boolean {
            val normalized = value.trim()
            val uri = runCatching { URI(normalized) }.getOrNull() ?: return false
            return uri.scheme.equals("https", true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null &&
                normalized.length in 9..2048
        }
    }
}
