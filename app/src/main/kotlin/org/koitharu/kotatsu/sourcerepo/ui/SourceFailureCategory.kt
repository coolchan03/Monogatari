package org.koitharu.kotatsu.sourcerepo.ui

import org.jsoup.HttpStatusException

/**
 * Triage categories for the source-health report; they do not indicate that
 * Monogatari can or should bypass a website's networking/security protections.
 *
 * Network and HTTP evidence is checked before script errors: a Mangayomi extension that
 * receives a 404 page usually fails later with a TypeError, and that is the website's
 * failure, not the extension's.
 */
internal fun sourceFailureCategory(description: String): String {
	val status = httpStatusIn(description)
	return when {
		description.contains("Unable to resolve host", ignoreCase = true) ||
			description.contains("UnknownHostException", ignoreCase = true) ||
			description.contains("No address associated with hostname", ignoreCase = true) -> "DNS"
		description.contains("Hostname", ignoreCase = true) &&
			description.contains("not verified", ignoreCase = true) -> "CERTIFICATE"
		description.contains("SSLPeerUnverifiedException", ignoreCase = true) ||
			description.contains("CertPathValidatorException", ignoreCase = true) ||
			description.contains("CertificateExpiredException", ignoreCase = true) -> "CERTIFICATE"
		description.contains("TLS packet", ignoreCase = true) ||
			description.contains("SSL", ignoreCase = true) -> "TLS"
		description.contains("CloudFlare", ignoreCase = true) ||
			description.contains("cf-mitigated", ignoreCase = true) -> "CLOUDFLARE"
		description.contains("Timeout", ignoreCase = true) ||
			description.contains("timed out", ignoreCase = true) -> "TIMEOUT"
		CONNECTION_MARKERS.any { description.contains(it, ignoreCase = true) } -> "CONNECTION"
		description.contains("Too many request", ignoreCase = true) ||
			description.contains("RateLimit", ignoreCase = true) || status == 429 -> "RATE_LIMIT"
		description.contains("InteractiveActionRequired", ignoreCase = true) -> "INTERACTIVE"
		description.contains("Authentication", ignoreCase = true) ||
			description.contains("AuthRequired", ignoreCase = true) || status == 401 -> "AUTH"
		status == 404 || status == 410 -> "HTTP_404"
		status == 403 -> "HTTP_403"
		status != null && status >= 500 -> "SERVER_ERROR"
		status != null && status >= 400 -> "HTTP_OTHER"
		description.contains("Unknown host call", ignoreCase = true) ||
			description.contains("Unsupported Mangayomi utility", ignoreCase = true) ||
			description.contains("is not supported yet", ignoreCase = true) -> "JS_RUNTIME_GAP"
		description.contains("TypeError", ignoreCase = true) ||
			description.contains("ReferenceError", ignoreCase = true) ||
			description.contains("SyntaxError", ignoreCase = true) ||
			description.contains("failed to load source", ignoreCase = true) -> "EXTENSION_SCRIPT"
		PARSER_MARKERS.any { description.contains(it, ignoreCase = true) } -> "PARSER"
		else -> "OTHER"
	}
}

/** Who most likely has to act on a failure category; used to group the copyable report. */
internal enum class SourceFailureCause(val title: String, val explanation: String) {
	UNREACHABLE(
		"Website unreachable",
		"The domain no longer resolves, refuses connections, times out or returns server errors. " +
			"Usually a dead or moved domain, a regional/ISP block or a temporary outage. " +
			"Check the device connection, try Settings > Network > DNS over HTTPS, then the source's current mirror.",
	),
	ACCESS_RESTRICTED(
		"Access restricted by the website",
		"The website answered but requires a browser check, sign-in, or refused or rate-limited automated access. " +
			"Open the source and complete the website's own verification; Monogatari does not bypass these protections.",
	),
	MOVED_CONTENT(
		"Page or API not found",
		"The website is online but the requested listing/API path is missing (HTTP 404/410). " +
			"The site changed its layout or domain; the parser or extension needs an upstream update.",
	),
	CODE_DEFECT(
		"Parser or extension defect",
		"The website responded but the parser/extension could not read it, or the extension needs a Mangayomi host " +
			"function Monogatari does not provide. Report it; these are fixable in code or by an extension update.",
	),
	SECURE_CONNECTION(
		"Secure connection problem",
		"TLS handshake or certificate validation failed. Often a misconfigured or expired site certificate, " +
			"or network interception. Monogatari will not disable certificate checks.",
	),
	UNKNOWN(
		"Unclassified",
		"The error did not match a known pattern. The copied line contains the raw error for a bug report.",
	),
}

internal fun sourceFailureCause(category: String): SourceFailureCause = when (category) {
	"DNS", "CONNECTION", "TIMEOUT", "SERVER_ERROR" -> SourceFailureCause.UNREACHABLE
	"CLOUDFLARE", "HTTP_403", "RATE_LIMIT", "AUTH", "INTERACTIVE", "HTTP_OTHER" -> SourceFailureCause.ACCESS_RESTRICTED
	"HTTP_404" -> SourceFailureCause.MOVED_CONTENT
	"PARSER", "EXTENSION_SCRIPT", "JS_RUNTIME_GAP" -> SourceFailureCause.CODE_DEFECT
	"TLS", "CERTIFICATE" -> SourceFailureCause.SECURE_CONNECTION
	else -> SourceFailureCause.UNKNOWN
}

/**
 * One-line description of a source failure that keeps the evidence the classifier needs:
 * HTTP status codes (jsoup's [HttpStatusException.message] omits them, and HTTP/2 responses
 * have no reason phrase at all) and the root network exception behind wrapper exceptions.
 */
internal fun describeSourceFailure(error: Throwable): String {
	val parts = ArrayList<String>(3)
	var current: Throwable? = error
	val seen = HashSet<Throwable>()
	while (current != null && parts.size < 3 && seen.add(current)) {
		val message = current.message.orEmpty().replace('\n', ' ').take(120)
		val status = (current as? HttpStatusException)?.let { " Status=${it.statusCode}" }.orEmpty()
		parts += "${current.javaClass.simpleName}: $message$status".trim()
		current = current.cause
	}
	return parts.joinToString(" <- ").replace('|', '/').take(320)
}

/** Recomputes the category of a saved report line, so results saved by older classifiers improve too. */
internal fun recategorizeSavedLine(line: String): String {
	if (!line.startsWith("FAIL |")) return line
	val match = CATEGORY_REGEX.find(line) ?: return line
	if (match.groupValues[1] != "OTHER") return line
	val updated = sourceFailureCategory(line.substring(match.range.last + 1))
	return if (updated == "OTHER") line else line.replaceRange(match.range, "category=$updated")
}

internal val CATEGORY_REGEX = Regex("""category=([A-Z0-9_]+)""")

private val STATUS_REGEX = Regex("""(?:Status=|HTTP |http_status=|status code:? )(\d{3})\b""", RegexOption.IGNORE_CASE)

private fun httpStatusIn(description: String): Int? =
	STATUS_REGEX.find(description)?.groupValues?.get(1)?.toIntOrNull()

private val CONNECTION_MARKERS = listOf(
	"ConnectException", "Connection refused", "Connection reset", "NoRouteToHostException",
	"Failed to connect", "unexpected end of stream", "EOFException", "Broken pipe", "Network is unreachable",
	"StreamResetException", "SocketException",
)

private val PARSER_MARKERS = listOf(
	"ParseException", "JSONException", "SerializationException", "JsonDecodingException",
	"NumberFormatException", "IndexOutOfBoundsException", "NoSuchElementException", "NullPointerException",
	"did not return an object", "Unexpected JSON",
)
