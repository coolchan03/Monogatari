package org.koitharu.kotatsu.core.media

import org.json.JSONObject

/** Describes honestly what Google Books lets you do with a volume (pure and unit-tested). Nothing here bypasses access limits. */
internal object GoogleBooksAccess {

	data class Access(val label: String, val freeToRead: Boolean, val epubUrl: String?)

	fun describe(volume: JSONObject): Access {
		val access = volume.optJSONObject("accessInfo")
		val viewability = access?.optString("viewability").orEmpty()
		val saleability = volume.optJSONObject("saleInfo")?.optString("saleability").orEmpty()
		val epub = access?.optJSONObject("epub")
		val epubUrl = epub?.takeIf { it.optBoolean("isAvailable") }
			?.optString("downloadLink")?.takeIf { it.startsWith("https://") }
		val free = viewability == "ALL_PAGES" || saleability == "FREE"
		val label = when {
			epubUrl != null -> "Free EPUB (official download)"
			viewability == "ALL_PAGES" -> "Free to read in full (official viewer)"
			viewability == "PARTIAL" -> "Preview of some pages (official)"
			saleability == "FOR_SALE" -> "For sale: preview not offered"
			else -> "Listing only"
		}
		return Access(label, free, epubUrl)
	}
}
