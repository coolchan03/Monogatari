package org.koitharu.kotatsu.sourcerepo.domain

import org.koitharu.kotatsu.jsext.repo.JsSourceEntry

/**
 * Repair only the malformed directory URLs published by some third-party JS indexes.
 * Do not rewrite complete script URLs or non-GitHub URLs.
 */
internal fun sourceCodeDownloadUrl(entry: JsSourceEntry): String {
    val url = entry.sourceCodeUrl
    if (!url.startsWith("https://raw.githubusercontent.com/", ignoreCase = true) ||
        !Regex("/javascript/(anime|manga|novel)/src/?$", RegexOption.IGNORE_CASE).containsMatchIn(url)
    ) return url
    val lang = entry.lang.lowercase().takeIf { it.matches(Regex("[a-z]{2,5}")) } ?: return url
    val filename = entry.name.lowercase().replace(Regex("[^a-z0-9]"), "")
        .takeIf { it.isNotEmpty() } ?: return url
    return url.trimEnd('/') + "/$lang/$filename.js"
}
