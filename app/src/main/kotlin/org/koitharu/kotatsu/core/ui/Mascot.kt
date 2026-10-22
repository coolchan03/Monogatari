package org.koitharu.kotatsu.core.ui

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import kotlin.math.min
import kotlin.math.roundToInt
import com.google.android.material.R as materialR

/** Kitsune, the spirit fox: one character that takes a different form for each kind of media. */
enum class MascotForm(@DrawableRes val drawable: Int, @StringRes val description: Int) {
	DEFAULT(R.drawable.kitsune_sticker_welcome, R.string.mascot_default),
	MANGA(R.drawable.kitsune_sticker_collection, R.string.mascot_manga),
	BOOK(R.drawable.kitsune_sticker_reading, R.string.mascot_book),
	VIDEO(R.drawable.kitsune_sticker_celebrate, R.string.mascot_video),
	AUDIO(R.drawable.kitsune_sticker_audio, R.string.mascot_audio),
	NEWS(R.drawable.kitsune_sticker_news, R.string.mascot_news),
	SAD(R.drawable.kitsune_sticker_sad, R.string.mascot_sad),
}

object Mascot {

	/** The form for a media tab; null (the "All" tab) is plain Kitsune. */
	fun formFor(type: MediaType?): MascotForm = when (type) {
		null -> MascotForm.DEFAULT
		MediaType.MANGA -> MascotForm.MANGA
		MediaType.BOOK -> MascotForm.BOOK
		MediaType.VIDEO -> MascotForm.VIDEO
		MediaType.AUDIO -> MascotForm.AUDIO
	}

	/**
	 * Shows [form] in [view] with an accessible description. The art follows the theme's primary colour (schemes,
	 * accent and Material You) and, when [framed], sits on a round theme-tinted backdrop so every screen shows
	 * Kitsune the same way in light, dark and AMOLED themes. A background the caller set itself is kept.
	 */
	fun bind(view: ImageView, form: MascotForm, framed: Boolean = true) {
		MascotArtwork.bind(view, form.drawable)
		view.contentDescription = view.context.getString(form.description)
		if (framed) frame(view)
	}

	/** Puts the round theme-tinted backdrop behind [view]; safe to call again after a theme change. */
	fun frame(view: ImageView) {
		val existing = view.background
		if (existing != null && existing !is MascotBackdrop) return
		val night = MascotArtwork.isNight(view)
		val fill = MaterialColors.getColor(
			view,
			if (night) materialR.attr.colorSecondaryContainer else materialR.attr.colorPrimaryContainer,
			Color.TRANSPARENT,
		)
		val backdrop = existing as? MascotBackdrop ?: MascotBackdrop()
		// Translucent so the card or surface below still shows through; on dark and AMOLED surfaces a thin
		// outline keeps the darker art from melting into the black.
		backdrop.setColor(ColorUtils.setAlphaComponent(fill, if (night) 170 else 210))
		if (night) {
			val stroke = MaterialColors.getColor(view, materialR.attr.colorOutlineVariant, Color.TRANSPARENT)
			backdrop.setStroke((view.resources.displayMetrics.density).roundToInt().coerceAtLeast(1), stroke)
		} else {
			backdrop.setStroke(0, Color.TRANSPARENT)
		}
		view.background = backdrop
		if (existing == null && view.paddingLeft == 0 && view.paddingTop == 0 && view.paddingRight == 0 && view.paddingBottom == 0) {
			view.doOnLayout { v ->
				val pad = (min(v.width, v.height) * FRAME_PADDING).roundToInt()
				if (pad > 0) v.setPadding(pad, pad, pad, pad)
			}
		}
	}

	private const val FRAME_PADDING = 0.08f

	/** Fully rounded: a circle for square mascot views, a pill for wide ones (GradientDrawable clamps the radius). */
	private class MascotBackdrop : GradientDrawable() {
		init {
			setShape(RECTANGLE)
			setCornerRadius(10_000f)
		}
	}

	/** Human form: the hidden adult Kitsune-kun, discovered by seven quick mascot taps. */
	fun showEasterEgg(activity: Activity) {
		val image = ImageView(activity).apply {
			MascotArtwork.bind(this, R.drawable.kitsune_sticker_human)
			contentDescription = activity.getString(R.string.mascot_egg_description)
			adjustViewBounds = true
			val pad = (16 * resources.displayMetrics.density).toInt()
			setPadding(pad, pad, pad, 0)
		}
		MaterialAlertDialogBuilder(activity)
			.setTitle(R.string.mascot_egg_title)
			.setMessage(R.string.mascot_egg_message)
			.setView(image)
			.setPositiveButton(R.string.mascot_egg_ok, null)
			.show()
	}
}

/** Counts quick repeated taps: true once [needed] taps land with no gap longer than [windowMs] (pure, unit-tested). */
class TapCounter(private val needed: Int = 7, private val windowMs: Long = 2000L) {

	private var count = 0
	private var last = Long.MIN_VALUE

	fun tap(nowMs: Long): Boolean {
		count = if (last != Long.MIN_VALUE && nowMs - last <= windowMs) count + 1 else 1
		last = nowMs
		if (count >= needed) {
			count = 0
			last = Long.MIN_VALUE
			return true
		}
		return false
	}
}
