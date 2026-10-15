package org.koitharu.kotatsu.core.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.MaterialToolbar
import kotlin.math.roundToInt

/**
 * Standard frame for the Monogatari screens that build their own views: a toolbar with title and Up button,
 * edge-to-edge inset handling (status/navigation bars, display cutouts, ChromeOS caption bars) and, for
 * text-heavy screens, a centred column with a readable maximum width on tablets and desktop windows.
 *
 * The app theme has no action bar, so without this those screens had no title and no way back on a laptop.
 */
object MonogatariChrome {

	/** Readable column for prose and forms. */
	const val TEXT_MAX_WIDTH_DP = 760

	/** Lists of cards: wide, but not edge to edge on a 1440dp window. */
	const val LIST_MAX_WIDTH_DP = 1200

	fun setContent(activity: AppCompatActivity, content: View, maxWidthDp: Int = 0) {
		val context: Context = activity
		val toolbar = MaterialToolbar(context).apply {
			title = activity.title
		}
		val column = MaxWidthFrameLayout(context, maxWidthDp).apply {
			addView(content, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
		}
		val root = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			addView(toolbar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
			addView(column, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
		}
		ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			toolbar.updatePadding(top = bars.top)
			root.updatePadding(left = bars.left, right = bars.right, bottom = bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
		activity.setContentView(root)
		activity.setSupportActionBar(toolbar)
		activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)
		toolbar.setNavigationOnClickListener { activity.onBackPressedDispatcher.onBackPressed() }
	}

	/** Applies system-bar insets as padding to a screen that already has its own toolbar. */
	fun applyInsets(root: View) {
		val start = root.paddingLeft
		val top = root.paddingTop
		val end = root.paddingRight
		val bottom = root.paddingBottom
		ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
			v.setPadding(start + bars.left, top + bars.top, end + bars.right, bottom + bars.bottom)
			WindowInsetsCompat.CONSUMED
		}
	}

	/** Grid columns for card lists: one per ~[cardWidthDp], at least 1, at most [max]. */
	fun columns(context: Context, cardWidthDp: Int = 340, max: Int = 4): Int =
		(context.resources.configuration.screenWidthDp / cardWidthDp).coerceIn(1, max)

	/** Centres its child and limits its width; re-measures on every window resize. */
	@SuppressLint("ViewConstructor")
	private class MaxWidthFrameLayout(context: Context, private val maxWidthDp: Int) : FrameLayout(context) {

		override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
			val maxPx = (maxWidthDp * resources.displayMetrics.density).roundToInt()
			val available = MeasureSpec.getSize(widthMeasureSpec)
			if (maxWidthDp > 0 && available > maxPx) {
				val side = (available - maxPx) / 2
				setPadding(side, paddingTop, side, paddingBottom)
			} else if (paddingLeft != 0 || paddingRight != 0) {
				setPadding(0, paddingTop, 0, paddingBottom)
			}
			super.onMeasure(widthMeasureSpec, heightMeasureSpec)
		}
	}
}

/** See [MonogatariChrome.setContent]. */
fun AppCompatActivity.setMonogatariContent(content: View, maxWidthDp: Int = 0) =
	MonogatariChrome.setContent(this, content, maxWidthDp)
