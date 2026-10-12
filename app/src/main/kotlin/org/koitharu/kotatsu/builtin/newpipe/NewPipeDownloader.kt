package org.koitharu.kotatsu.builtin.newpipe

import okhttp3.OkHttpClient
import okhttp3.Request as HttpRequest
import okhttp3.RequestBody.Companion.toRequestBody
import org.koitharu.kotatsu.core.network.MangaHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as ExtractorRequest
import org.schabi.newpipe.extractor.downloader.Response as ExtractorResponse
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Minimal OkHttp bridge for NewPipeExtractor. No browser/captcha activity is spawned. */
@Singleton
class NewPipeDownloader @Inject constructor(
    @MangaHttpClient private val client: OkHttpClient,
) : Downloader() {
    @Throws(IOException::class)
    override fun execute(request: ExtractorRequest): ExtractorResponse {
        val builder = HttpRequest.Builder().url(request.url())
        request.headers().forEach { (name, values) ->
            values.forEach { value -> builder.addHeader(name, value) }
        }
        val method = request.httpMethod().uppercase()
        val body = request.dataToSend()
        val httpRequest = builder.method(
            method,
            if (method in setOf("POST", "PUT", "PATCH")) (body ?: byteArrayOf()).toRequestBody() else null,
        ).build()
        return client.newCall(httpRequest).execute().use { response ->
            ExtractorResponse(
                response.code,
                response.message,
                response.headers.toMultimap(),
                if (method == "HEAD") "" else response.body?.string().orEmpty(),
                response.request.url.toString(),
            )
        }
    }
}
