package org.koitharu.kotatsu.sourcerepo.domain

import java.net.URI

/**
 * Normalises repository URLs. HTTP requires an explicit opt-in at each add.
 */
fun normalizeRepoUrl(input: String, allowHttp: Boolean = false): String? {
	val trimmed = input.trim()
	if (!trimmed.startsWith("https://", ignoreCase = true) &&
		!(allowHttp && trimmed.startsWith("http://", ignoreCase = true))) return null
	val uri = try {
		URI(trimmed)
	} catch (e: Exception) {
		return null
	}
	if (uri.host.isNullOrEmpty() || uri.userInfo != null || uri.fragment != null) return null
	if (uri.scheme?.lowercase() !in listOf("https", "http")) return null
	if (uri.scheme.equals("http", true) && !allowHttp) return null
	return uri.normalize().toString()
}
