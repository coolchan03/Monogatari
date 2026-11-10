package org.koitharu.kotatsu.core.network

import java.util.Locale

/** Strict hostname comparison. Never use substring checks for origin decisions. */
object HostMatcher {

	/** True when [host] is exactly [baseHost] or a real subdomain of it (case-insensitive, trailing dot ignored). */
	fun isSameSite(host: String?, baseHost: String?): Boolean {
		val h = normalize(host) ?: return false
		val b = normalize(baseHost) ?: return false
		return h == b || h.endsWith(".$b")
	}

	/** Only plain web navigations may stay inside a source WebView (no intent:, file:, javascript:, data:). */
	fun isWebScheme(scheme: String?): Boolean {
		val s = scheme?.lowercase(Locale.ROOT)
		return s == "http" || s == "https"
	}

	private fun normalize(host: String?): String? =
		host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
}
