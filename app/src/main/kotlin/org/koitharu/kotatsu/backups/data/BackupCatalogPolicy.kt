package org.koitharu.kotatsu.backups.data

import java.net.URI
import java.net.URLDecoder
import java.util.Locale

/** Keep authorization-bearing catalog URLs and preference keys out of shared backup archives. */
internal object BackupCatalogPolicy {
    private val secretWords = listOf("password", "token", "secret", "cookie",
        "apikey", "api_key", "auth", "credential", "session", "signature", "sig", "proxy_login")
    fun safeUrl(raw: String, httpsOnly: Boolean): String? {
        val value = raw.trim()
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "https" && (httpsOnly || scheme != "http")) return null
        if (uri.host.isNullOrBlank() || uri.rawUserInfo != null || uri.rawFragment != null) return null
        val keys = uri.rawQuery.orEmpty().split('&').map { param ->
            var name = param.substringBefore('=')
            repeat(2) {
                name = runCatching { URLDecoder.decode(name, "UTF-8") }.getOrNull() ?: return null
            }
            name.lowercase(Locale.ROOT)
        }
        if (keys.any { k -> secretWords.any { w -> w in k } }) return null
        return uri.normalize().toString()
    }

    fun safePreferenceKey(key: String): Boolean {
        val name = key.lowercase(Locale.ROOT)
        return secretWords.none { it in name }
    }
}
