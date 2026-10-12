package org.koitharu.kotatsu.jsext.source

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.koitharu.kotatsu.core.exceptions.resolve.CaptchaAutoResolveCoordinator
import org.koitharu.kotatsu.core.ui.util.ForegroundActivityHolder
import org.koitharu.kotatsu.jsext.JsHttpRequest
import org.koitharu.kotatsu.jsext.JsHttpResponse
import org.koitharu.kotatsu.jsext.JsHttpTransport
import org.koitharu.kotatsu.jsext.bool
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.await

/** Sends a JS extension's requests through the app's scraping client (cookies, Cloudflare, rate limits). */
class JsOkHttpTransport(
	private val client: OkHttpClient,
	private val source: MangaSource? = null,
	allowedHosts: Set<String> = emptySet(),
	private val captchaCoordinator: CaptchaAutoResolveCoordinator? = null,
	private val foregroundActivityHolder: ForegroundActivityHolder? = null,
) : JsHttpTransport {

	private val cookieScope = ScopedCookieJar(client.cookieJar, allowedHosts)
	private val scopedClient = client.newBuilder().cookieJar(cookieScope).build()

	override suspend fun execute(request: JsHttpRequest): JsHttpResponse {
		// Handle the challenge at the HTTP boundary, before QuickJS can turn its native cause into
		// a JavaScript error string. Use Kotatsu's existing solver, shared cookie jar and cooldowns.
		val activeSource = source
		val coordinator = captchaCoordinator
		return if (activeSource != null && coordinator != null) {
			coordinator.runWithVerification(
				source = activeSource,
				mayStartVerification = foregroundActivityHolder?.current != null,
			) { executeOnce(request) }
		} else {
			executeOnce(request)
		}
	}

	private suspend fun executeOnce(request: JsHttpRequest): JsHttpResponse {
		val effectiveClient = if (request.options?.get("followRedirects").bool() == false) {
			scopedClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
		} else {
			scopedClient
		}
		val effectiveUrl = compatibleJsSourceUrl(request.url)
		val builder = Request.Builder().url(effectiveUrl)
		if (source != null) {
			// Kotatsu's CloudFlareInterceptor reads this tag and routes CAPTCHA challenges through
			// the same resolver used by built-in parser sources.
			builder.tag(MangaSource::class.java, source)
		}
		request.headers.forEach { (name, value) ->
			// Never forward an extension-supplied cookie to a host outside its own scope.
			if (name.equals("Cookie", ignoreCase = true) && !cookieScope.allows(builder.build().url.host)) return@forEach
			// Let OkHttp provide the new Host header when an obsolete Comick API host is remapped.
			if (effectiveUrl != request.url && name.equals("Host", ignoreCase = true) &&
				value.equals("api.comick.fun", ignoreCase = true)) return@forEach
			builder.header(name, value)
		}
		val contentType = request.headers.entries
			.firstOrNull { it.key.equals("content-type", ignoreCase = true) }?.value?.toMediaTypeOrNull()
		val needsBody = request.method == "POST" || request.method == "PUT" || request.method == "PATCH"
		val body = when {
			request.body != null -> request.body.toRequestBody(contentType)
			needsBody -> ByteArray(0).toRequestBody(contentType)
			else -> null
		}
		builder.method(request.method, body)
		return effectiveClient.newCall(builder.build()).await().use { response ->
			val responseBody = response.body
			val charset = responseBody.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
			val raw = if (request.method == "HEAD") ByteArray(0) else responseBody.bytes()
			JsHttpResponse(
				statusCode = response.code,
				reasonPhrase = response.message,
				headers = response.headers.names().associate { name ->
					name.lowercase() to response.headers.values(name).joinToString(", ")
				},
				body = if (request.method == "HEAD" || request.binary) "" else raw.toString(charset),
				finalUrl = response.request.url.toString(),
				isRedirect = response.isRedirect,
				bodyBytes = if (request.method == "HEAD") null else raw,
			)
		}
	}
}
