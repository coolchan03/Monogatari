package org.koitharu.kotatsu.builtin.opds

/**
 * One-tap public OPDS catalogs. Only feeds that were fetched and answered with an Atom/OPDS
 * document (content type application/atom+xml) are listed here.
 *
 * Checked and deliberately left out: Standard Ebooks (the OPDS feed needs a Patrons Circle login),
 * Feedbooks, Internet Archive bookserver, Open Library and ManyBooks (no reachable OPDS root).
 */
object OpdsPresets {
	data class Preset(val name: String, val url: String, val note: String)

	val all: List<Preset> = listOf(
		Preset("Project Gutenberg", "https://www.gutenberg.org/ebooks.opds/", "Public domain ebooks, 70,000+ titles"),
		Preset("Project Gutenberg: latest", "https://www.gutenberg.org/ebooks/search.opds/?sort_order=release_date", "Newest Gutenberg releases"),
		Preset("Unglue.it", "https://www.unglue.it/api/opds/", "Free and Creative Commons ebooks"),
	)

	/** Presets must be https and must parse into a stable OPDS source id. */
	fun isValid(preset: Preset): Boolean =
		preset.url.startsWith("https://") && runCatching { OpdsSource.fromUrl(preset.url) }.isSuccess

	fun isAdded(preset: Preset): Boolean {
		val id = runCatching { OpdsSource.fromUrl(preset.url).name }.getOrNull() ?: return false
		return OpdsSourceRegistry.fromName(id) != null
	}
}
