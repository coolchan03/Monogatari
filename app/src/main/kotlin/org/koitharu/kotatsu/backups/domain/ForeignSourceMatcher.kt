package org.koitharu.kotatsu.backups.domain

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

/** Dry-run source resolution for foreign backups; ambiguous titles must await user review. */
internal object ForeignSourceMatcher {
    data class Source(val id: String, val name: String, val website: String?)
    data class Requested(val sourceId: String?, val sourceName: String?, val website: String?)
    enum class Confidence { EXACT_ID, UNIQUE_HOST, UNIQUE_NAME, NEEDS_REVIEW }
    data class Decision(val candidate: Source?, val confidence: Confidence)

    /** Cross-app numeric source IDs are NOT portable; trust IDs only for the same ecosystem. */
    fun resolve(requested: Requested, available: Collection<Source>, trustSourceIds: Boolean = false): Decision {
        val foreignHost = host(requested.website)
        // An explicitly supplied but invalid URL must never fall back to name matching.
        if (!requested.website.isNullOrBlank() && foreignHost == null) {
            return Decision(null, Confidence.NEEDS_REVIEW)
        }
        val id = requested.sourceId?.trim().orEmpty()
        val byId = available.filter { id.isNotEmpty() && it.id == id }
        if (trustSourceIds && byId.size == 1 && hostCompatible(foreignHost, byId.single().website)) {
            return Decision(byId.single(), Confidence.EXACT_ID)
        }
        if (foreignHost != null) {
            val matches = available.filter { host(it.website) == foreignHost }
            if (matches.size == 1) return Decision(matches.single(), Confidence.UNIQUE_HOST)
            return Decision(null, Confidence.NEEDS_REVIEW)
        }
        val normalizedName = normalizeName(requested.sourceName)
        if (normalizedName.isNotEmpty()) {
            val matches = available.filter { normalizeName(it.name) == normalizedName }
            if (matches.size == 1) return Decision(matches.single(), Confidence.UNIQUE_NAME)
        }
        return Decision(null, Confidence.NEEDS_REVIEW)
    }

    private fun hostCompatible(expected: String?, candidateUrl: String?): Boolean =
        expected == null || host(candidateUrl) == expected

    private fun host(raw: String?): String? {
        val url = raw?.trim()?.toHttpUrlOrNull() ?: return null
        if (url.scheme != "https") return null
        return url.host.removePrefix("www.").removePrefix("m.").takeIf { it.isNotBlank() }
    }

    private fun normalizeName(raw: String?): String = raw.orEmpty().trim()
        .lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}
