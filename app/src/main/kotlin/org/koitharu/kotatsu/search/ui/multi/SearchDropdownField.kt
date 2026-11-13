package org.koitharu.kotatsu.search.ui.multi

import android.content.Context
import android.text.InputType
import android.view.ViewGroup
import androidx.annotation.StringRes
import com.google.android.material.R as MaterialR
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

/**
 * A Material exposed-dropdown replacing the native Spinner in search filter forms, so the filters match the
 * rest of the app (outlined field, floating label, 48dp target, TalkBack label).
 */
internal class SearchDropdownField(
	context: Context,
	@StringRes label: Int,
	private val options: List<String>,
	selectedIndex: Int,
) {
	/** Index into the list given at construction. */
	var selectedIndex: Int = selectedIndex.coerceIn(0, (options.size - 1).coerceAtLeast(0))
		private set

	val view: TextInputLayout = TextInputLayout(
		context, null, MaterialR.attr.textInputOutlinedExposedDropdownMenuStyle,
	).apply {
		hint = context.getString(label)
		layoutParams = ViewGroup.MarginLayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
		).apply { topMargin = (8 * context.resources.displayMetrics.density).toInt() }
	}

	init {
		val field = MaterialAutoCompleteTextView(view.context).apply {
			inputType = InputType.TYPE_NULL
			setSimpleItems(options.toTypedArray())
			options.getOrNull(this@SearchDropdownField.selectedIndex)?.let { setText(it, false) }
			setOnItemClickListener { _, _, position, _ -> this@SearchDropdownField.selectedIndex = position }
			minimumHeight = (48 * context.resources.displayMetrics.density).toInt()
			contentDescription = context.getString(label)
		}
		view.addView(field, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	}
}
