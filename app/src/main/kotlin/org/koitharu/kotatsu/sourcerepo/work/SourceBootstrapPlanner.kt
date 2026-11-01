package org.koitharu.kotatsu.sourcerepo.work

import org.koitharu.kotatsu.jsext.JsItemType
import org.koitharu.kotatsu.jsext.repo.JsSourceEntry
import org.koitharu.kotatsu.jsext.repo.compareVersions
import org.koitharu.kotatsu.sourcehealth.SourceWebsiteGroups
import java.util.Locale

/** Conservatively skip only sources clearly duplicated by an enabled parser or installed JS site. */
object SourceBootstrapPlanner {
    fun normalizedHost(baseUrl: String): String? = SourceWebsiteGroups.host(baseUrl)

    private fun shortAlias(value: String): String =
        value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun hostLabel(host: String): String = shortAlias(host.substringBefore('.'))

    private fun websiteKey(entry: JsSourceEntry): String =
        (normalizedHost(entry.baseUrl) ?: "id:${entry.id}") + "|" + entry.itemType.name

    private val accountGatedWords = listOf("torrent", "debrid")

    /** Sources that do nothing without an external debrid/torrent account; install them one by one. */
    fun needsExternalAccount(entry: JsSourceEntry): Boolean {
        val name = entry.name.lowercase(Locale.ROOT)
        return accountGatedWords.any { it in name }
    }

    fun plan(
        catalog: List<JsSourceEntry>,
        installed: List<JsSourceEntry>,
        enabledParserNames: Set<String>,
        includeAdult: Boolean,
        types: Set<JsItemType> = JsItemType.entries.toSet(),
        includeOtherLanguages: Boolean = false,
    ): List<JsSourceEntry> {
        val installedIds = installed.mapTo(HashSet()) { it.id }
        val installedSites = installed.mapTo(HashSet()) { websiteKey(it) }
        val parserAliases = enabledParserNames.mapTo(HashSet()) { shortAlias(it) }
        return catalog.asSequence()
            .filter { it.isRunnable && it.id !in installedIds }
            .filter { it.itemType in types && !needsExternalAccount(it) }
            .filter {
                val language = it.lang.lowercase(Locale.ROOT)
                includeOtherLanguages || language == "en" || language == "all" || language.startsWith("en-")
            }
            .filter { includeAdult || !it.isNsfw }
            .filter { websiteKey(it) !in installedSites }
            .filter { entry ->
                val host = normalizedHost(entry.baseUrl) ?: return@filter true
                val label = hostLabel(host)
                // Short generic labels ('manga', 'read') are not reliable deduplication evidence.
                label.length < 6 || label !in parserAliases
            }
            .groupBy(::websiteKey)
            .values.mapNotNull { group ->
                group.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
            }
            .sortedWith(compareBy<JsSourceEntry> { it.itemType.name }
                .thenBy { normalizedHost(it.baseUrl) ?: it.name.lowercase(Locale.ROOT) })
    }
}
