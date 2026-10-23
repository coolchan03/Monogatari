package org.koitharu.kotatsu.backups.domain

import java.util.Locale
import java.util.zip.ZipEntry

enum class BackupSection(
	val entryName: String,
) {

	INDEX("index"),
	HISTORY("history"),
	CATEGORIES("categories"),
	FAVOURITES("favourites"),
	SETTINGS("settings"),
	SETTINGS_READER_GRID("reader_grid"),
	BOOKMARKS("bookmarks"),
	SOURCES("sources"),
    REPOS("repos"),
    JS_SOURCES("js_sources"),
    OPDS_CATALOGS("opds_catalogs"),
    SOURCE_SETTINGS("source_settings"),
	SCROBBLING("scrobbling"),
	STATS("statistics"),
	CHAPTER_META("chapter_meta"),
	SAVED_FILTERS("saved_filters"),

	/**
	 * The community identity key.
	 *
	 * Included in the backup because it is the only thing that carries a user's comments and ratings
	 * to a new phone - losing it orphans them permanently, and a recovery phrase nobody wrote down is
	 * not a backup.
	 */
	IDENTITY("identity"),
	;

	companion object {

		/**
		 * Everything except the community identity key: automatic backups (periodic, Android auto-backup, folder sync)
		 * must not carry a bearer credential. The manual Create backup action passes `entries.toSet()` so a copy the
		 * user makes by hand still lets them recover their community account; restoring it works as before.
		 */
		val ROUTINE_EXPORT: Set<BackupSection> by lazy { entries.filterTo(LinkedHashSet()) { it != IDENTITY } }

		fun of(entry: ZipEntry): BackupSection? {
			val name = entry.name.lowercase(Locale.ROOT)
			return entries.find { x -> x.entryName == name }
		}
	}
}
