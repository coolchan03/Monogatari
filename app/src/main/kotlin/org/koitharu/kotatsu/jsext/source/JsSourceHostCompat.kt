package org.koitharu.kotatsu.jsext.source

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Upgrade one retired upstream API hostname to its published successor.
 * URL parsing and exact host matching avoid rewriting lookalike domains.
 * TLS verification remains enabled; errors from the new endpoint still surface.
 */
internal fun compatibleJsSourceUrl(rawUrl: String): String {
    val url = rawUrl.toHttpUrlOrNull() ?: return rawUrl
    if (url.isHttps && url.host == "api.comick.fun") {
        return url.newBuilder().host("api.comick.dev").build().toString()
    }
    return rawUrl
}
