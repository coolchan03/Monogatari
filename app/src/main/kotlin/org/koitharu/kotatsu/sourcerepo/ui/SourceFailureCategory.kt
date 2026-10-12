package org.koitharu.kotatsu.sourcerepo.ui

/**
 * Triage categories for the source-health report; they do not indicate that
 * Monogatari can or should bypass a website's networking/security protections.
 */
internal fun sourceFailureCategory(description: String): String = when {
    description.contains("Unable to resolve host", ignoreCase = true) ||
        description.contains("UnknownHostException", ignoreCase = true) -> "DNS"
    description.contains("Hostname", ignoreCase = true) &&
        description.contains("not verified", ignoreCase = true) -> "CERTIFICATE"
    description.contains("SSLPeerUnverifiedException", ignoreCase = true) ||
        description.contains("CertPathValidatorException", ignoreCase = true) -> "CERTIFICATE"
    description.contains("TLS packet", ignoreCase = true) ||
        description.contains("SSL", ignoreCase = true) -> "TLS"
    description.contains("CloudFlare", ignoreCase = true) -> "CLOUDFLARE"
    description.contains("Too many request", ignoreCase = true) ||
        description.contains("RateLimit", ignoreCase = true) -> "RATE_LIMIT"
    description.contains("Timeout", ignoreCase = true) ||
        description.contains("timed out", ignoreCase = true) -> "TIMEOUT"
    description.contains("TypeError", ignoreCase = true) ||
        description.contains("ReferenceError", ignoreCase = true) ||
        description.contains("SyntaxError", ignoreCase = true) -> "EXTENSION_SCRIPT"
    description.contains("Authentication", ignoreCase = true) ||
        description.contains("AuthRequired", ignoreCase = true) -> "AUTH"
    description.contains("InteractiveActionRequired", ignoreCase = true) -> "INTERACTIVE"
    Regex("""(?:Status=|HTTP )404\b""").containsMatchIn(description) -> "HTTP_404"
    Regex("""(?:Status=|HTTP )403\b""").containsMatchIn(description) -> "HTTP_403"
    else -> "OTHER"
}
