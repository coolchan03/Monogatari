package org.koitharu.kotatsu.core.network

import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.internal.closeQuietly
import org.koitharu.kotatsu.parsers.exception.TooManyRequestExceptions

class RateLimitInterceptor : Interceptor {
	override fun intercept(chain: Interceptor.Chain): Response {
		val response = chain.proceed(chain.request())
		if (response.code == 429) {
			val request = response.request
			response.closeQuietly()
			throw TooManyRequestExceptions(
				url = request.url.toString(),
				retryAfter = RetryAfterParser.parseDelayMillis(response.header(CommonHeaders.RETRY_AFTER)),
			)
		}
		return response
	}
}
