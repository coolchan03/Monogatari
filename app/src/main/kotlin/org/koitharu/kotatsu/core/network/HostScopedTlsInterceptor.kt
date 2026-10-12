package org.koitharu.kotatsu.core.network

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.net.ssl.SSLException

/** Provides the failed request host to the SSL exception resolver. */
class SourceSslException(val requestHost: String, original: SSLException) :
    SSLException("TLS certificate verification failed for $requestHost") {
    init {
        initCause(original)
    }
}

/**
 * Certificate bypass is limited to explicitly approved manga hosts.
 * The insecure client has redirects disabled, so it cannot follow a redirect to another domain.
 * Never add this interceptor to the base client (sync, updates, accounts, etc.).
 */
class HostScopedTlsInterceptor(
    private val settings: AppSettings,
    private val unsafeClient: OkHttpClient,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()
        return try {
            if (allowsHost(host, settings.sslBypassHosts)) {
                unsafeClient.newCall(request).execute()
            } else {
                chain.proceed(request)
            }
        } catch (e: SSLException) {
            throw SourceSslException(host, e)
        }
    }

    companion object {
        /** Never allow suffix matching for TLS exceptions. */
        fun allowsHost(host: String, allowed: Set<String>): Boolean =
            host.lowercase() in allowed.map { it.lowercase() }.toSet()
    }
}
