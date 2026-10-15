package org.koitharu.kotatsu.local.data

enum class CacheDir(val dir: String) {

	THUMBS("image_cache"),
	FAVICONS("favicons"),
	PAGES("pages"),

	/** Pages translated by `reader.translate.PageTranslator`. */
	TRANSLATED_PAGES("translated_pages");
}
