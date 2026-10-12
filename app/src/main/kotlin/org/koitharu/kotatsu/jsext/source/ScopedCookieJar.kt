package org.koitharu.kotatsu.jsext.source

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/** Allow source cookies only on the source's own site and its subdomains. */
class ScopedCookieJar(
    private val delegate: CookieJar,
    allowedHosts: Set<String>,
) : CookieJar {
    private val hosts = allowedHosts.map { it.lowercase().trimEnd('.') }.filter { it.isNotEmpty() }.toSet()

    fun allows(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        return hosts.any { normalized == it || normalized.endsWith(".$it") }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (allows(url.host)) delegate.loadForRequest(url) else emptyList()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (allows(url.host)) delegate.saveFromResponse(url, cookies)
    }
}
