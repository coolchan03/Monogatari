package org.koitharu.kotatsu.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.URI
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * A user-clicked, permission-respecting offline import of a publisher-supplied free EPUB.
 * Never attempts protected borrowing, DRM removal, login scraping or arbitrary URL download.
 */
internal object OfficialEpubDownloader {
    private const val MAX_DOWNLOAD = 32L * 1024 * 1024
    private const val MAX_UNCOMPRESSED = 384L * 1024 * 1024
    private val GUTENBERG_EPUB = Regex("\\.epub(?:3)?(?:\\.(?:images|noimages))?$", RegexOption.IGNORE_CASE)

    fun approved(catalog: String, url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.userInfo != null || uri.fragment != null) return false
        if (uri.port != -1 && uri.port != 443) return false
        val path = uri.path.orEmpty()
        if (path.contains("..") || path.contains('\\')) return false
        return when (catalog) {
            "Standard Ebooks" -> StandardEbooksCatalog.officialPage(url) &&
                "/downloads/" in path && path.endsWith(".epub", ignoreCase = true)
            "Project Gutenberg" -> uri.host?.lowercase() in
                setOf("gutenberg.org", "www.gutenberg.org") &&
                (path.startsWith("/ebooks/") || path.startsWith("/cache/epub/")) &&
                GUTENBERG_EPUB.containsMatchIn(path)
            else -> false
        }
    }

    suspend fun save(client: OkHttpClient, libraryDir: File, title: String, catalog: String, url: String): File =
        withContext(Dispatchers.IO) {
            if (!approved(catalog, url)) throw IOException("This catalog did not provide an approved free EPUB URL")
            if (!libraryDir.isDirectory && !libraryDir.mkdirs()) throw IOException("Cannot create the offline book library")
            val digest = MessageDigest.getInstance("SHA-256").digest(url.toByteArray(Charsets.UTF_8))
                .take(8).joinToString("") { "%02x".format(it) }
            val filename = title.trim().replace(Regex("[^\\p{L}\\p{N}._ -]"), "_")
                .trim(' ', '.').take(80).ifBlank { "ebook" }
            val destination = File(libraryDir, "book-$digest-$filename.epub")
            if (destination.isFile && destination.length() > 0) return@withContext destination
            val temporary = File.createTempFile("book-$digest-", ".partial", libraryDir)
            try {
                var attempt = 1
                while (true) {
                    try {
                        download(client, catalog, url, temporary)
                        break
                    } catch (e: TransientDownloadException) {
                        // Brief network drops and publisher overloads are retried; safety refusals never are.
                        if (attempt >= MAX_ATTEMPTS) throw IOException(e.message, e.cause)
                        delay(RETRY_DELAYS_MS[attempt - 1])
                        attempt++
                    }
                }
                validateEpub(temporary)
                if (!temporary.renameTo(destination)) {
                    if (!destination.isFile) throw IOException("Unable to save the downloaded EPUB")
                }
                return@withContext destination
            } finally {
                temporary.delete()
            }
        }

    /** Failures worth another attempt: connection problems, truncated bodies, HTTP 408/429/5xx. */
    private class TransientDownloadException(message: String, cause: Throwable? = null) : IOException(message, cause)

    private const val MAX_ATTEMPTS = 3
    private val RETRY_DELAYS_MS = longArrayOf(1_000L, 3_000L)

    internal fun isTransientStatus(code: Int): Boolean = code == 408 || code == 429 || code in 500..599

    private suspend fun download(client: OkHttpClient, catalog: String, url: String, temporary: File) {
        val request = Request.Builder().url(url).get().build()
        val call = try {
            client.newCall(request).execute()
        } catch (e: IOException) {
            throw TransientDownloadException("Could not reach the publisher: ${e.message.orEmpty()}".trim(), e)
        }
        call.use { response ->
            if (isTransientStatus(response.code)) {
                throw TransientDownloadException("The publisher is busy (HTTP ${response.code}); try again later")
            }
            if (!response.isSuccessful) throw IOException("The publisher returned HTTP ${response.code}")
            if (!approved(catalog, response.request.url.toString())) {
                throw IOException("The publisher redirected to an unapproved download location")
            }
            val body = response.body ?: throw IOException("The publisher returned an empty download")
            if (body.contentLength() > MAX_DOWNLOAD) throw IOException("The EPUB exceeds the size limit")
            val expected = body.contentLength()
            var total = 0L
            body.byteStream().use { input ->
                // Overwrite any partial bytes from a previous attempt.
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(16_384)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = try {
                            input.read(buffer)
                        } catch (e: IOException) {
                            throw TransientDownloadException("The download was interrupted", e)
                        }
                        if (count < 0) break
                        total += count
                        if (total > MAX_DOWNLOAD) throw IOException("The EPUB exceeds the size limit")
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (total == 0L) throw IOException("The publisher returned an empty download")
            if (expected > 0 && total < expected) throw TransientDownloadException("The download was cut off")
        }
    }

    private fun validateEpub(file: File) {
        ZipFile(file).use { zip ->
            val mimetype = zip.getEntry("mimetype") ?: throw IOException("Not a valid EPUB: mimetype is missing")
            if (mimetype.size !in 1L..64L) throw IOException("Invalid EPUB mimetype")
            val value = zip.getInputStream(mimetype).use { input ->
                val bytes = ByteArray(64)
                val size = input.read(bytes)
                if (size <= 0) "" else String(bytes, 0, size, Charsets.US_ASCII)
            }
            if (value != "application/epub+zip") throw IOException("The file is not an EPUB")
            var expanded = 0L
            var entries = 0
            val contents = zip.entries()
            while (contents.hasMoreElements()) {
                val entry = contents.nextElement()
                entries++
                if (entries > 12_000 || entry.name.startsWith("/") ||
                    entry.name.contains('\\') || entry.name.split('/').any { it == ".." }
                ) throw IOException("The EPUB archive contains unsafe entries")
                val length = entry.size.coerceAtLeast(0)
                if (length > MAX_UNCOMPRESSED - expanded) throw IOException("The EPUB archive is too large")
                expanded += length
            }
        }
    }
}
