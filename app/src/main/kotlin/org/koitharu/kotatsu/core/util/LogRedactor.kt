package org.koitharu.kotatsu.core.util

import java.util.Locale

/**
 * Removes credentials from text before it is logged or copied into a diagnostic report:
 * sensitive URL query parameters and userinfo, sensitive headers, cookies, and key/value pairs in
 * JSON or form-encoded bodies.
 */
object LogRedactor {

	const val MASK = "[redacted]"

	private val SENSITIVE_KEY_PARTS = listOf(
		"password", "passwd", "pwd", "pass", "secret", "token", "authorization", "auth", "apikey",
		"api_key", "api-key", "key", "session", "sessionid", "sid", "cookie", "signature", "sig",
		"credential", "bearer", "jwt", "otp", "pin", "code", "csrf", "xsrf",
	)

	private val SENSITIVE_HEADERS = setOf(
		"authorization", "proxy-authorization", "cookie", "set-cookie", "x-api-key", "x-auth-token",
		"x-csrf-token", "x-xsrf-token",
	)

	fun isSensitiveKey(name: String): Boolean {
		val n = name.lowercase(Locale.ROOT).trim()
		if (n.isEmpty()) return false
		return SENSITIVE_KEY_PARTS.any { part -> n == part || n.contains(part) && part.length > 3 } ||
			n.endsWith("_key") || n.endsWith("-key") || n.endsWith("key") && n.length > 3 && n != "monkey"
	}

	fun isSensitiveHeader(name: String): Boolean {
		val n = name.lowercase(Locale.ROOT).trim()
		return n in SENSITIVE_HEADERS || isSensitiveKey(n)
	}

	fun header(name: String, value: String): String = if (isSensitiveHeader(name)) MASK else value

	// key=value in query strings and form bodies
	private val FORM_PAIR = Regex("""([^&=\s?#"']+)=([^&\s#"']*)""")

	// "key": "value" or "key": value in JSON-ish text
	private val JSON_PAIR = Regex("""("(?:[^"\\]|\\.)*"\s*:\s*)("(?:[^"\\]|\\.)*"|[^,}\]\s{\[][^,}\]]*)""")

	// scheme://user:pass@host
	private val USERINFO = Regex("""(?i)([a-z][a-z0-9+.-]*://)[^/\s@]+@""")

	// Authorization-style inline values: "Bearer abc", "Basic abc"
	private val INLINE_BEARER = Regex("""(?i)\b(bearer|basic|token)\s+[A-Za-z0-9._~+/=-]{8,}""")

	// Inline "Header: value" lines for sensitive headers
	private val HEADER_LINE = Regex("""(?im)^(\s*)([A-Za-z0-9-]+)(\s*:\s*)(.+)$""")

	/** Redacts any free-form text: URLs, header dumps, JSON and form bodies, stack traces. */
	fun redact(text: String): String {
		var out = text
		out = USERINFO.replace(out) { it.groupValues[1] + MASK + "@" }
		out = HEADER_LINE.replace(out) { m ->
			if (isSensitiveHeader(m.groupValues[2])) "${m.groupValues[1]}${m.groupValues[2]}${m.groupValues[3]}$MASK" else m.value
		}
		out = JSON_PAIR.replace(out) { m ->
			val key = m.groupValues[1].substringBefore(':').trim().trim('"')
			if (isSensitiveKey(key)) {
				val quoted = m.groupValues[2].startsWith("\"")
				m.groupValues[1] + if (quoted) "\"$MASK\"" else MASK
			} else m.value
		}
		out = FORM_PAIR.replace(out) { m ->
			if (isSensitiveKey(m.groupValues[1])) "${m.groupValues[1]}=$MASK" else m.value
		}
		out = INLINE_BEARER.replace(out) { it.groupValues[1] + " " + MASK }
		return out
	}

	/** Convenience for log lines that mention a URL. */
	fun url(url: Any?): String = redact(url?.toString().orEmpty())
}
