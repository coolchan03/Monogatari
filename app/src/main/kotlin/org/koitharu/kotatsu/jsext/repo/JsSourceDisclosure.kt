package org.koitharu.kotatsu.jsext.repo

import java.net.URI
import java.time.Instant
import java.util.Locale

/** Repository metadata is untrusted and never implies an Android permission grant. */
internal object JsSourceDisclosure {
    private fun clean(raw: String?, limit: Int = 120) =
        raw.orEmpty().replace(Regex("[\\r\\n\\t]+"), " ").trim().take(limit)

    fun indexState(entry: JsSourceEntry, nowMs: Long = System.currentTimeMillis()): String {
        val status = clean(entry.indexStatus, 30).lowercase(Locale.ROOT)
        val reportedAt = runCatching {
            entry.indexCheckedAt?.let { Instant.parse(it).toEpochMilli() }
        }.getOrNull()
        return when (status) {
            "ok", "working", "healthy" -> "Index: working (publisher report)"
            "new" -> "Index: new (not yet tested)"
            "updating" -> "Index: updating"
            "broken" -> {
                if (reportedAt == null || nowMs - reportedAt > 7L * 86_400_000L ||
                    reportedAt > nowMs + 5 * 60_000L)
                    "Index: outdated failure; retest"
                else "Index: broken (publisher report)"
            }
            else -> "Index: not verified"
        }
    }

    private fun host(url: String): String = runCatching {
        val uri = URI(url)
        if (uri.scheme.equals("https", true) && uri.userInfo == null)
            uri.host?.takeIf(String::isNotBlank)?.take(100) else null
    }.getOrNull() ?: "not provided"

    fun details(entry: JsSourceEntry): String {
        val permissions = entry.declaredPermissions
            .map { clean(it, 120) }.filter(String::isNotBlank).distinct().take(12)
        return buildString {
            appendLine("Source: " + clean(entry.name))
            appendLine("Media: " + entry.itemType.name.lowercase(Locale.ROOT))
            appendLine("Language: " + clean(entry.lang).ifBlank { "not provided" })
            appendLine("Version: " + clean(entry.version).ifBlank { "not provided" })
            appendLine(indexState(entry))
            appendLine("Last publisher check: " +
                clean(entry.indexCheckedAt, 40).ifBlank { "not supplied" })
            appendLine("Publisher/author: " +
                clean(entry.author).ifBlank { "not supplied" })
            appendLine("License: " + clean(entry.license).ifBlank { "not supplied" })
            appendLine("Content website: " + host(entry.baseUrl))
            appendLine("Extension code: " + host(entry.sourceCodeUrl))
            appendLine("Source repository: " + host(entry.repoUrl))
            appendLine("Declared capabilities: " +
                if (entry.hasCloudflare) "May encounter Cloudflare/CAPTCHA" else
                    "No Cloudflare requirement declared")
            appendLine("Declared permissions: " +
                if (permissions.isEmpty()) "not supplied by repository"
                else permissions.joinToString(", "))
            append("Repository claims are not an independent security check. " +
                "Third-party source code may make network requests through the " +
                "extension engine. Review the source before installation.")
        }
    }
}
