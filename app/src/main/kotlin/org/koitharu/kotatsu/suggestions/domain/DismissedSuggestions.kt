package org.koitharu.kotatsu.suggestions.domain

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Titles the user marked "Not interested". Stored on this device only (never uploaded) so that a refresh of the
 * suggestions does not bring them back. Removing an id here is what Undo does.
 */
@Singleton
class DismissedSuggestions @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

	fun ids(): Set<Long> = prefs.getStringSet(KEY_IDS, null).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

	@Synchronized
	fun add(ids: Collection<Long>) = update { it += ids.map(Long::toString) }

	@Synchronized
	fun remove(ids: Collection<Long>) = update { it -= ids.map(Long::toString).toSet() }

	private inline fun update(block: (MutableSet<String>) -> Unit) {
		// Copy first: the set returned by SharedPreferences must not be modified in place.
		val copy = HashSet(prefs.getStringSet(KEY_IDS, null).orEmpty())
		block(copy)
		prefs.edit { putStringSet(KEY_IDS, copy) }
	}

	private companion object {

		const val PREFS_NAME = "suggestions_dismissed"
		const val KEY_IDS = "ids"
	}
}
