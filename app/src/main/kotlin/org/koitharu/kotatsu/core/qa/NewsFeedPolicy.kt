package org.koitharu.kotatsu.core.qa

import java.net.URI

/** Any public HTTPS RSS/Atom address; local, private-network and credential-bearing URLs are refused. */
internal object NewsFeedPolicy {
    private val IPV4 = Regex("^(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})$")

    fun trustedFeed(url: String): Boolean = runCatching {
        val uri = URI(url)
        val host = uri.host?.lowercase()?.trimEnd('.')
        uri.scheme.equals("https", ignoreCase = true) &&
            !host.isNullOrBlank() && !privateHost(host) &&
            uri.userInfo == null && uri.fragment == null && uri.rawQuery?.length.orZero() < 512 &&
            url.length <= 2048
    }.getOrDefault(false)

    /** Loopback, link-local, private ranges, IPv6 literals and single-label or .local names. */
    fun privateHost(host: String): Boolean {
        val name = host.lowercase().removePrefix("[").removeSuffix("]").trimEnd('.')
        if (name.isEmpty() || ':' in name) return true
        if (name == "localhost" || name.endsWith(".localhost") || name.endsWith(".local") ||
            name.endsWith(".internal") || name.endsWith(".lan") || name.endsWith(".home.arpa") || '.' !in name) {
            return true
        }
        val octets = IPV4.matchEntire(name)?.groupValues?.drop(1)?.map { it.toInt() } ?: return false
        if (octets.any { it > 255 }) return true
        val (a, b) = octets
        return a == 0 || a == 10 || a == 127 || a >= 224 ||
            (a == 169 && b == 254) || (a == 172 && b in 16..31) || (a == 192 && b == 168) ||
            (a == 100 && b in 64..127)
    }

    private fun Int?.orZero() = this ?: 0
}
