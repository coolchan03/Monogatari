package org.koitharu.kotatsu.core.ui

import android.app.Activity
import android.widget.ImageView
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType

/** Kitsune, the spirit fox: one character that takes a different form for each kind of media. */
enum class MascotForm(@DrawableRes val drawable: Int, @StringRes val description: Int) {
	DEFAULT(R.drawable.kitsune_sticker_welcome, R.string.mascot_default),
	MANGA(R.drawable.kitsune_sticker_collection, R.string.mascot_manga),
	BOOK(R.drawable.kitsune_sticker_reading, R.string.mascot_book),
	VIDEO(R.drawable.kitsune_sticker_celebrate, R.string.mascot_video),
	AUDIO(R.drawable.kitsune_sticker_rest, R.string.mascot_audio),
	NEWS(R.drawable.kitsune_sticker_search, R.string.mascot_news),
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

	/** Shows [form] in [view] with an accessible description. */
	fun bind(view: ImageView, form: MascotForm) {
		MascotArtwork.bind(view, form.drawable)
		view.contentDescription = view.context.getString(form.description)
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
