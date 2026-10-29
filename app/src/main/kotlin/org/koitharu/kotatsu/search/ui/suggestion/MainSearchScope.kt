package org.koitharu.kotatsu.search.ui.suggestion

import android.content.Context
import androidx.core.content.edit
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType

/**
 * The single media type every search uses: "ALL" or a [MediaType] name. The main search bar, the results
 * screen chips and deep calls all read and write this value; Explore's own media filter is not involved.
 */
object MainSearchScope {

	private const val PREFS = "monogatari_search"
	private const val KEY_SCOPE = "main_search_media_type"

	val scopes: List<String> = SearchScopeRules.scopes

	fun get(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		.getString(KEY_SCOPE, null)?.takeIf { it in scopes }

	/** Never null: the saved value, else "ALL". */
	fun current(context: Context): String = get(context) ?: SearchScopeRules.ALL

	fun set(context: Context, scope: String) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY_SCOPE, scope) }
	}

	fun label(context: Context, scope: String?): String = context.getString(
		when (scope) {
			MediaType.MANGA.name -> R.string.search_media_manga
			MediaType.BOOK.name -> R.string.search_media_books
			MediaType.VIDEO.name -> R.string.search_media_video
			MediaType.AUDIO.name -> R.string.search_media_audio
			else -> R.string.search_media_all
		},
	)
}
