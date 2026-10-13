package org.koitharu.kotatsu.core.qa

import java.net.URI

/** Step 64: allow-listed HTTPS RSS endpoints; no network fetch is performed. */
internal object NewsFeedPolicy {
    private val permittedHosts = setOf("animenewsnetwork.com", "www.animenewsnetwork.com",
        "www.youtube.com", "youtube.com", "www.reddit.com", "reddit.com")
    fun trustedFeed(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme.equals("https", ignoreCase = true) &&
            uri.host?.lowercase() in permittedHosts &&
            uri.userInfo == null && uri.fragment == null && uri.rawQuery?.length.orZero() < 512
    }.getOrDefault(false)

    private fun Int?.orZero() = this ?: 0
}
