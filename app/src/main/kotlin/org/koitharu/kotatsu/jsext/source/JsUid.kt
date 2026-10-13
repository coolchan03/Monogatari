package org.koitharu.kotatsu.jsext.source

private const val UID_SEED = 1125899906842597L

/**
 * Stable id of a manga, chapter or page of a JS source: the same scheme as the parsers' `generateUid`
 * (source name + url). Shared with the backup importer so imported titles get the ids the source produces.
 */
fun jsUid(sourceName: String, url: String): Long {
	var h = UID_SEED
	sourceName.forEach { c -> h = 31 * h + c.code }
	url.forEach { c -> h = 31 * h + c.code }
	return h
}
