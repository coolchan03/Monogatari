package org.koitharu.kotatsu.search.ui.multi

import android.annotation.SuppressLint
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityViewCommand
import androidx.core.view.isVisible
import org.koitharu.kotatsu.R
import kotlin.math.roundToInt

/**
 * Applies a [SearchPaneArrangement] to the results list, the draggable divider and the preview pane.
 * The divider can be dragged, moved with the arrow keys, or driven by accessibility actions.
 */
internal class SearchPaneController(
	private val panes: LinearLayout,
	private val list: View,
	private val divider: View,
	private val preview: View,
	private val prefs: SearchPanePrefs,
) {
	var arrangement = SearchPaneArrangement.LIST_ONLY
		private set
	private var fraction = 1f

	init {
		setupDivider()
	}

	/**
	 * @param hasSelection while nothing is selected the preview stays out of the way: a one-line hint below the
	 * list, or nothing at all beside it. The divider only appears once there is something to resize.
	 */
	fun apply(newArrangement: SearchPaneArrangement, hasSelection: Boolean) {
		arrangement = newArrangement
		fraction = prefs.fraction(newArrangement)
		val side = newArrangement == SearchPaneArrangement.PREVIEW_SIDE
		val split = newArrangement != SearchPaneArrangement.LIST_ONLY
		val compactHint = split && !hasSelection
		preview.isVisible = split && !(compactHint && side)
		divider.isVisible = split && hasSelection
		panes.orientation = if (side) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
		val handle = (48 * panes.resources.displayMetrics.density).roundToInt()
		list.layoutParams = LinearLayout.LayoutParams(
			if (side) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
			if (side) LinearLayout.LayoutParams.MATCH_PARENT else 0,
			if (split && hasSelection) fraction else 1f,
		)
		divider.layoutParams = LinearLayout.LayoutParams(
			if (side) handle else LinearLayout.LayoutParams.MATCH_PARENT,
			if (side) LinearLayout.LayoutParams.MATCH_PARENT else handle,
			0f,
		)
		divider.setBackgroundResource(if (side) R.drawable.search_pane_handle_v else R.drawable.search_pane_handle_h)
		preview.layoutParams = when {
			compactHint -> LinearLayout.LayoutParams(
				LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 0f,
			)
			else -> LinearLayout.LayoutParams(
				if (side) 0 else LinearLayout.LayoutParams.MATCH_PARENT,
				if (side) LinearLayout.LayoutParams.MATCH_PARENT else 0,
				if (split) 1f - fraction else 0f,
			)
		}
		announceSplit()
	}

	private fun setFraction(value: Float, persist: Boolean) {
		if (arrangement == SearchPaneArrangement.LIST_ONLY || !divider.isVisible) return
		fraction = SearchPaneLayoutPolicy.clampFraction(value)
		(list.layoutParams as LinearLayout.LayoutParams).weight = fraction
		(preview.layoutParams as LinearLayout.LayoutParams).weight = 1f - fraction
		panes.requestLayout()
		if (persist) prefs.setFraction(arrangement, fraction)
		announceSplit()
	}

	private fun announceSplit() {
		val percent = ((1f - fraction) * 100).roundToInt()
		ViewCompat.setStateDescription(divider, divider.context.getString(R.string.search_pane_split_state, percent))
	}

	private fun isRtl() = panes.layoutDirection == View.LAYOUT_DIRECTION_RTL

	@SuppressLint("ClickableViewAccessibility")
	private fun setupDivider() {
		divider.setOnTouchListener { v, event ->
			when (event.actionMasked) {
				MotionEvent.ACTION_DOWN -> {
					v.parent?.requestDisallowInterceptTouchEvent(true)
					true
				}

				MotionEvent.ACTION_MOVE -> {
					val loc = IntArray(2)
					panes.getLocationOnScreen(loc)
					val next = if (arrangement == SearchPaneArrangement.PREVIEW_SIDE) {
						val x = (event.rawX - loc[0]) / panes.width.coerceAtLeast(1)
						if (isRtl()) 1f - x else x
					} else {
						(event.rawY - loc[1]) / panes.height.coerceAtLeast(1)
					}
					setFraction(next, persist = false)
					true
				}

				MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
					v.parent?.requestDisallowInterceptTouchEvent(false)
					prefs.setFraction(arrangement, fraction)
					true
				}

				else -> false
			}
		}
		divider.setOnKeyListener { _, keyCode, event ->
			if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener false
			val side = arrangement == SearchPaneArrangement.PREVIEW_SIDE
			val shrinkList = if (side) {
				if (isRtl()) KeyEvent.KEYCODE_DPAD_RIGHT else KeyEvent.KEYCODE_DPAD_LEFT
			} else KeyEvent.KEYCODE_DPAD_UP
			val growList = if (side) {
				if (isRtl()) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
			} else KeyEvent.KEYCODE_DPAD_DOWN
			when (keyCode) {
				shrinkList -> { nudge(-STEP); true }
				growList -> { nudge(STEP); true }
				else -> false
			}
		}
		val larger = AccessibilityViewCommand { _, _ -> nudge(-STEP); true }
		val smaller = AccessibilityViewCommand { _, _ -> nudge(STEP); true }
		ViewCompat.addAccessibilityAction(divider, divider.context.getString(R.string.search_preview_larger), larger)
		ViewCompat.addAccessibilityAction(divider, divider.context.getString(R.string.search_preview_smaller), smaller)
	}

	private fun nudge(delta: Float) = setFraction(fraction + delta, persist = true)

	private companion object {
		const val STEP = 0.05f
	}
}
