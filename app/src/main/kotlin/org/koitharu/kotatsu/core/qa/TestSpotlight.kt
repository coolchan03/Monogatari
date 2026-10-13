package org.koitharu.kotatsu.core.qa

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.ActionMenuView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.children
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Guided-test spotlight: after "Open feature" in the Test Center, every screen of the app shows a small card
 * with the step's instructions and "Looks good" / "Needs fixing" buttons, and a pulsing ring is drawn around
 * the control being tested (when it's on screen). The answer is saved straight into the Test Center report.
 * Touches pass through the ring, so the app stays fully usable while it's shown.
 */
object TestSpotlight {

	const val PREFS_NAME = "monogatari_guided_tests_v1"
	private const val TAG = "monogatari_test_spotlight"

	private class Armed(val case: TestCase)

	private var armed: Armed? = null
	private var callbacks: Application.ActivityLifecycleCallbacks? = null

	fun arm(activity: Activity, case: TestCase) {
		val app = activity.application
		disarm(app)
		armed = Armed(case)
		val cb = object : Application.ActivityLifecycleCallbacks {
			override fun onActivityResumed(activity: Activity) {
				if (activity !is MonogatariTestCenterActivity) attach(activity)
			}

			override fun onActivityPaused(activity: Activity) = detach(activity)
			override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
			override fun onActivityStarted(activity: Activity) = Unit
			override fun onActivityStopped(activity: Activity) = Unit
			override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
			override fun onActivityDestroyed(activity: Activity) = Unit
		}
		callbacks = cb
		app.registerActivityLifecycleCallbacks(cb)
	}

	fun disarm(app: Application) {
		callbacks?.let(app::unregisterActivityLifecycleCallbacks)
		callbacks = null
		armed = null
	}

	private fun attach(activity: Activity) {
		val case = armed?.case ?: return
		val decor = activity.window?.decorView as? ViewGroup ?: return
		if (decor.findViewWithTag<View>(TAG) != null) return
		val overlay = Overlay(activity, case)
		overlay.tag = TAG
		decor.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
	}

	private fun detach(activity: Activity) {
		val decor = activity.window?.decorView as? ViewGroup ?: return
		decor.findViewWithTag<View>(TAG)?.let(decor::removeView)
	}

	private fun record(activity: Activity, case: TestCase, result: CheckResult, note: String?) {
		val prefs = activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
			.putString("result_${case.id}", result.name)
		if (!note.isNullOrBlank()) prefs.putString("note_${case.id}", note.trim())
		prefs.apply()
		detach(activity)
		disarm(activity.application)
		Toast.makeText(activity, "Saved: ${result.label}", Toast.LENGTH_SHORT).show()
	}

	@SuppressLint("ViewConstructor")
	private class Overlay(private val activity: Activity, private val case: TestCase) : FrameLayout(activity) {

		private val density = resources.displayMetrics.density
		private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
			style = Paint.Style.STROKE
			color = RING_COLOR
		}
		private val target = RectF()
		private var hasTarget = false
		private var lastLookup = 0L
		private var phase = 0f
		private val card: LinearLayout
		private var cardAtTop = true
		private var insetTop = 0
		private var insetBottom = 0
		private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
			duration = 1200L
			repeatCount = ValueAnimator.INFINITE
			interpolator = LinearInterpolator()
			addUpdateListener {
				phase = it.animatedValue as Float
				updateTarget()
				invalidate()
			}
		}

		init {
			setWillNotDraw(false)
			card = buildCard()
			addView(card, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT, Gravity.TOP))
			ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
				val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
				insetTop = bars.top
				insetBottom = bars.bottom
				placeCard()
				insets
			}
			placeCard()
		}

		override fun onAttachedToWindow() {
			super.onAttachedToWindow()
			ViewCompat.requestApplyInsets(this)
			animator.start()
		}

		override fun onDetachedFromWindow() {
			animator.cancel()
			super.onDetachedFromWindow()
		}

		override fun onDraw(canvas: Canvas) {
			super.onDraw(canvas)
			if (!hasTarget) return
			// Two rings: a solid one hugging the control and a fading one pulsing outwards.
			val base = 6f * density
			drawRing(canvas, base, 4f * density, 255)
			drawRing(canvas, base + 14f * density * phase, 3f * density, (255 * (1f - phase)).toInt())
		}

		private fun drawRing(canvas: Canvas, pad: Float, stroke: Float, alpha: Int) {
			ringPaint.strokeWidth = stroke
			ringPaint.alpha = alpha
			val r = RectF(target.left - pad, target.top - pad, target.right + pad, target.bottom + pad)
			val corner = minOf(r.width(), r.height()) / 2f
			canvas.drawRoundRect(r, corner, corner, ringPaint)
		}

		private fun updateTarget() {
			val now = SystemClock.uptimeMillis()
			if (now - lastLookup < LOOKUP_INTERVAL_MS) return
			lastLookup = now
			val view = findTarget()
			hasTarget = view != null
			if (view == null) return
			val loc = IntArray(2)
			val own = IntArray(2)
			view.getLocationInWindow(loc)
			getLocationInWindow(own)
			target.set(
				(loc[0] - own[0]).toFloat(),
				(loc[1] - own[1]).toFloat(),
				(loc[0] - own[0] + view.width).toFloat(),
				(loc[1] - own[1] + view.height).toFloat(),
			)
		}

		private fun findTarget(): View? {
			val root = activity.window?.decorView ?: return null
			for (id in case.highlight) {
				root.findViewById<View>(id)?.takeIf { it.isShown && it.width > 0 }?.let { return it }
			}
			return if (case.highlightOverflow) findOverflow(root) else null
		}

		private fun findOverflow(view: View): View? {
			if (view === this) return null
			if (view is ActionMenuView) {
				view.children.firstOrNull { (it.layoutParams as? ActionMenuView.LayoutParams)?.isOverflowButton == true }
					?.takeIf { it.isShown }?.let { return it }
			}
			if (view is ViewGroup) {
				for (child in view.children) findOverflow(child)?.let { return it }
			}
			return null
		}

		private fun placeCard() {
			val lp = card.layoutParams as LayoutParams
			val margin = (12 * density).toInt()
			lp.gravity = if (cardAtTop) Gravity.TOP else Gravity.BOTTOM
			lp.setMargins(margin, margin + if (cardAtTop) insetTop + (48 * density).toInt() else 0, margin, margin + if (cardAtTop) 0 else insetBottom)
			card.layoutParams = lp
		}

		private fun buildCard(): LinearLayout {
			val pad = (12 * density).toInt()
			return LinearLayout(context).apply {
				orientation = LinearLayout.VERTICAL
				setPadding(pad, pad, pad, pad / 2)
				isClickable = true
				elevation = 8 * density
				background = GradientDrawable().apply {
					cornerRadius = 16 * density
					setColor(CARD_COLOR)
					setStroke((2 * density).toInt(), RING_COLOR)
				}
				addView(TextView(context).apply {
					text = "Testing: ${case.title}  ⇅"
					setTextColor(Color.WHITE)
					setTypeface(typeface, Typeface.BOLD)
					textSize = 15f
					// Tap the title to move the card out of the way.
					setOnClickListener {
						cardAtTop = !cardAtTop
						placeCard()
					}
				})
				addView(TextView(context).apply {
					text = case.instructions
					setTextColor(Color.argb(230, 255, 255, 255))
					textSize = 13f
					maxLines = 5
					setPadding(0, pad / 3, 0, pad / 3)
				})
				addView(LinearLayout(context).apply {
					orientation = LinearLayout.HORIZONTAL
					addView(button("👍 Looks good") { record(activity, case, CheckResult.PASS, null) }, weighted())
					addView(button("👎 Needs fixing") { askWhatIsWrong() }, weighted())
					addView(button("✕") {
						detach(activity)
						disarm(activity.application)
					})
				})
			}
		}

		private fun askWhatIsWrong() {
			if (activity !is AppCompatActivity) {
				record(activity, case, CheckResult.FAIL, null)
				return
			}
			val input = EditText(activity).apply { hint = "What's wrong? (optional)" }
			val pad = (20 * density).toInt()
			val container = FrameLayout(activity).apply {
				setPadding(pad, pad / 2, pad, 0)
				addView(input)
			}
			MaterialAlertDialogBuilder(activity)
				.setTitle(case.title)
				.setView(container)
				.setNegativeButton(android.R.string.cancel, null)
				.setPositiveButton(android.R.string.ok) { _, _ ->
					record(activity, case, CheckResult.FAIL, input.text?.toString())
				}
				.show()
		}

		private fun button(label: String, onClick: () -> Unit) = Button(context, null, android.R.attr.borderlessButtonStyle).apply {
			text = label
			isAllCaps = false
			setTextColor(Color.WHITE)
			setOnClickListener { onClick() }
		}

		private fun weighted() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

		private companion object {
			const val LOOKUP_INTERVAL_MS = 250L
			val RING_COLOR = Color.rgb(255, 64, 129)
			val CARD_COLOR = Color.argb(235, 33, 33, 40)
		}
	}
}
