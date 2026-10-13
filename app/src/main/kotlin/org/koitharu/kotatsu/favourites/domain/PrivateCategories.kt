package org.koitharu.kotatsu.favourites.domain

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.content.edit
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Favourite categories marked private are hidden from the library, "all favourites", search and widgets until
 * the user unlocks them (screen lock / biometrics). They lock again as soon as the app goes to the background.
 */
@Singleton
class PrivateCategories @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	private val ids = MutableStateFlow(load())
	private val unlocked = MutableStateFlow(false)

	val privateIds: StateFlow<Set<Long>> = ids.asStateFlow()
	val isUnlocked: StateFlow<Boolean> = unlocked.asStateFlow()

	/** Categories to hide right now: all private ones while locked, none once unlocked. */
	val hiddenIds: Flow<Set<Long>> = combine(ids, unlocked) { set, isUnlocked -> if (isUnlocked) emptySet() else set }

	init {
		Handler(Looper.getMainLooper()).post {
			ProcessLifecycleOwner.get().lifecycle.addObserver(
				object : DefaultLifecycleObserver {
					override fun onStop(owner: LifecycleOwner) = lock()
				},
			)
		}
	}

	fun isPrivate(categoryId: Long): Boolean = categoryId in ids.value

	/** Hidden right now? */
	fun isHidden(categoryId: Long): Boolean = !unlocked.value && categoryId in ids.value

	fun setPrivate(categoryId: Long, isPrivate: Boolean) {
		ids.update { if (isPrivate) it + categoryId else it - categoryId }
		prefs.edit { putStringSet(KEY_IDS, ids.value.mapTo(HashSet()) { it.toString() }) }
	}

	fun unlock() {
		unlocked.value = true
	}

	fun lock() {
		unlocked.value = false
	}

	private fun load(): Set<Long> =
		prefs.getStringSet(KEY_IDS, null).orEmpty().mapNotNullTo(HashSet()) { it.toLongOrNull() }

	private companion object {

		const val PREFS_NAME = "private_categories"
		const val KEY_IDS = "ids"
	}
}
