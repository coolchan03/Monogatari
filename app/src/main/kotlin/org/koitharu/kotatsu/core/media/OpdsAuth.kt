package org.koitharu.kotatsu.core.media

import java.net.URI

/**
 * Propagate a user's OPDS Basic credentials only to HTTPS requests on the SAME origin.
 * Deliberately excludes subdomains, redirects to new hosts, and HTTP downgrades.
 * This is independent from app backup policy: secrets stay in user preferences.
 */
internal object OpdsAuth {
    fun headerFor(catalogUrl: String, targetUrl: String): String? {
        val (baseUrl, header) = OpdsParser.splitCredentials(catalogUrl)
        if (header == null) return null
        val origin = runCatching { URI(baseUrl) }.getOrNull() ?: return null
        val target = runCatching { URI(targetUrl) }.getOrNull() ?: return null
        if (!origin.scheme.equals("https", ignoreCase = true)) return null
        if (!target.scheme.equals("https", ignoreCase = true)) return null
        if (!origin.host.equals(target.host, ignoreCase = true)) return null
        if (origin.host == null || target.host == null || target.rawUserInfo != null) return null
        fun port(uri: URI) = if (uri.port == -1) 443 else uri.port
        if (port(origin) != port(target)) return null
        return header
    }

    /** Never render a saved OPDS URL with embedded username/password on screen. */
    fun displayUrl(url: String): String = OpdsParser.splitCredentials(url).first
}
