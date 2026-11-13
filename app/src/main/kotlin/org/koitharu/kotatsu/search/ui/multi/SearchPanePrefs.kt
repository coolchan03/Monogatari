package org.koitharu.kotatsu.search.ui.multi

import android.content.Context
import androidx.core.content.edit

/** Remembers the preview layout mode and the divider position per arrangement (side/stacked). */
class SearchPanePrefs(context: Context) {

	private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

	var mode: SearchPaneMode
		get() = SearchPaneMode.fromName(prefs.getString(KEY_MODE, null))
		set(value) = prefs.edit { putString(KEY_MODE, value.name) }

	fun fraction(arrangement: SearchPaneArrangement): Float {
		if (arrangement == SearchPaneArrangement.LIST_ONLY) return 1f
		val saved = prefs.getFloat(keyFor(arrangement), Float.NaN)
		return if (saved.isNaN()) SearchPaneLayoutPolicy.defaultFraction(arrangement)
		else SearchPaneLayoutPolicy.clampFraction(saved)
	}

	fun setFraction(arrangement: SearchPaneArrangement, value: Float) {
		if (arrangement == SearchPaneArrangement.LIST_ONLY) return
		prefs.edit { putFloat(keyFor(arrangement), SearchPaneLayoutPolicy.clampFraction(value)) }
	}

	private fun keyFor(arrangement: SearchPaneArrangement) = "fraction_" + arrangement.name

	private companion object {
		const val PREFS = "monogatari_search_panes"
		const val KEY_MODE = "mode"
	}
}
