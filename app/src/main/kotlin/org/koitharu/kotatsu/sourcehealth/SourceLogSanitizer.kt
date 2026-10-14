package org.koitharu.kotatsu.sourcehealth

/** Redact common bearer credentials before storing, presenting or copying diagnostics. */
internal object SourceLogSanitizer {
    private val bearer = Regex("""(?i)\b(bearer\s+)[A-Za-z0-9_.~+/-]{8,}""")
    private val query = Regex("""(?i)([?&](?:api[_-]?key|token|access[_-]?token|x-plex-token|password|client_secret)=)[^&#\s]+""")
    private val authHeader = Regex("""(?i)\b(authorization|client[_-]?secret|api[_-]?key|password)\s*[:=]\s*(?:(?:basic|bearer)\s+)?[^\s;,&]+""")
    private val userInfo = Regex("""(?i)(https?://)[^/@\s]+@""")

    fun redact(message: String): String = userInfo.replace(
        authHeader.replace(
            query.replace(
                bearer.replace(message) { it.groupValues[1] + "[redacted]" }
            ) { it.groupValues[1] + "[redacted]" }
        ) { it.groupValues[1] + "=[redacted]" }
    ) { it.groupValues[1] + "[redacted]@" }
}
