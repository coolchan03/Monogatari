package org.koitharu.kotatsu.core.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.setThemeTextAppearance
import kotlin.math.roundToInt
import com.google.android.material.R as materialR

/*
 * Shared building blocks for the Monogatari screens that build their views in code, so every secondary screen
 * matches the More hub and the libraries: tonal cards (like item_monogatari_settings_destination), a Kitsune
 * header, filled primary / outlined secondary buttons and theme text appearances that follow the font scale.
 */

/** [value] density-independent pixels in pixels. */
fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

/** Padded vertical column for a page of cards; wrap it with [scrollable] when the page can grow. */
fun Context.pageColumn(): LinearLayout = LinearLayout(this).apply {
	orientation = LinearLayout.VERTICAL
	val pad = dp(16)
	setPadding(pad, pad, pad, pad)
}

/** A ScrollView holding this view, filling the viewport so short pages still lay out from the top. */
fun View.scrollable(): ScrollView = ScrollView(context).also { scroll ->
	scroll.isFillViewport = true
	scroll.addView(this, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
}

/** A flat tonal card, as used by the More rows: surface-container colour, 12dp corners, no stroke or shadow. */
fun Context.monogatariCard(): MaterialCardView = MaterialCardView(this).apply {
	setCardBackgroundColor(getThemeColor(materialR.attr.colorSurfaceContainerLow))
	radius = dp(12).toFloat()
	strokeWidth = 0
	cardElevation = 0f
}

private fun LinearLayout.cardParams() = LinearLayout.LayoutParams(
	ViewGroup.LayoutParams.MATCH_PARENT,
	ViewGroup.LayoutParams.WRAP_CONTENT,
).apply { bottomMargin = context.dp(12) }

/**
 * Kitsune header card: the mascot in [form] beside a TitleMedium [title] and a BodyMedium body.
 * Returns the body TextView so a screen can use it as its status line. Pass [mascotSizeDp] smaller for
 * screens where content (a player or a web page) needs the space.
 */
fun Context.headerCard(
	form: MascotForm,
	title: CharSequence?,
	body: CharSequence?,
	mascotSizeDp: Int = 64,
): Pair<MaterialCardView, TextView> {
	val card = monogatariCard()
	val row = LinearLayout(this).apply {
		orientation = LinearLayout.HORIZONTAL
		gravity = Gravity.CENTER_VERTICAL
		val pad = dp(16)
		setPadding(pad, dp(12), pad, dp(12))
	}
	val mascot = ImageView(this).apply {
		scaleType = ImageView.ScaleType.FIT_CENTER
		Mascot.bind(this, form)
	}
	row.addView(mascot, LinearLayout.LayoutParams(dp(mascotSizeDp), dp(mascotSizeDp)))
	val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
	if (!title.isNullOrEmpty()) {
		texts.addView(TextView(this).apply {
			text = title
			setThemeTextAppearance(materialR.attr.textAppearanceTitleMedium, materialR.style.TextAppearance_Material3_TitleMedium)
			ViewCompat.setAccessibilityHeading(this, true)
		}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	}
	val bodyView = bodyText(body).apply {
		visibility = if (body.isNullOrEmpty() && !title.isNullOrEmpty()) View.GONE else View.VISIBLE
		if (!title.isNullOrEmpty()) setPadding(0, dp(4), 0, 0)
	}
	texts.addView(bodyView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
		marginStart = dp(16)
	})
	card.addView(row)
	return card to bodyView
}

/** Adds a [headerCard] to this column and returns its body TextView. */
fun LinearLayout.addHeaderCard(
	form: MascotForm,
	title: CharSequence?,
	body: CharSequence?,
	mascotSizeDp: Int = 64,
): TextView {
	val (card, bodyView) = context.headerCard(form, title, body, mascotSizeDp)
	addView(card, cardParams())
	return bodyView
}

/** A card with a TitleSmall heading in the primary colour; returns the card and the column to fill. */
fun Context.sectionCard(title: CharSequence?): Pair<MaterialCardView, LinearLayout> {
	val card = monogatariCard()
	val body = LinearLayout(this).apply {
		orientation = LinearLayout.VERTICAL
		val pad = dp(16)
		setPadding(pad, dp(12), pad, pad)
	}
	if (!title.isNullOrEmpty()) {
		body.addView(TextView(this).apply {
			text = title
			setThemeTextAppearance(materialR.attr.textAppearanceTitleSmall, materialR.style.TextAppearance_Material3_TitleSmall)
			setTextColor(getThemeColor(androidx.appcompat.R.attr.colorPrimary))
			ViewCompat.setAccessibilityHeading(this, true)
			setPadding(0, 0, 0, dp(4))
		}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	}
	card.addView(body)
	return card to body
}

/** Adds a [sectionCard] to this column and returns the column inside it. */
fun LinearLayout.addSectionCard(title: CharSequence?): LinearLayout {
	val (card, body) = context.sectionCard(title)
	addView(card, cardParams())
	return body
}

private fun Context.styledButton(styleAttr: Int, label: CharSequence, onClick: () -> Unit) =
	MaterialButton(this, null, styleAttr).apply {
		text = label
		minHeight = dp(48)
		setOnClickListener { onClick() }
	}

/** Filled button for the main action of a screen or section. */
fun Context.primaryButton(label: CharSequence, onClick: () -> Unit): MaterialButton =
	styledButton(materialR.attr.materialButtonStyle, label, onClick)

/** Outlined button for every other action. */
fun Context.secondaryButton(label: CharSequence, onClick: () -> Unit): MaterialButton =
	styledButton(materialR.attr.materialButtonOutlinedStyle, label, onClick)

/** Full-width layout params for a button in a column, spaced like the Settings dialogs. */
fun Context.buttonParams(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
	ViewGroup.LayoutParams.MATCH_PARENT,
	ViewGroup.LayoutParams.WRAP_CONTENT,
).apply { topMargin = dp(6) }

fun LinearLayout.addPrimaryButton(label: CharSequence, onClick: () -> Unit): MaterialButton =
	context.primaryButton(label, onClick).also { addView(it, context.buttonParams()) }

fun LinearLayout.addSecondaryButton(label: CharSequence, onClick: () -> Unit): MaterialButton =
	context.secondaryButton(label, onClick).also { addView(it, context.buttonParams()) }

/** Body text that follows the theme typography and the system font scale. */
fun Context.bodyText(text: CharSequence?): TextView = TextView(this).apply {
	this.text = text
	setThemeTextAppearance(materialR.attr.textAppearanceBodyMedium, materialR.style.TextAppearance_Material3_BodyMedium)
}

fun LinearLayout.addBodyText(text: CharSequence?): TextView =
	context.bodyText(text).also {
		addView(it, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	}

/** Secondary-colour status line; TalkBack announces changes. */
fun Context.statusText(text: CharSequence? = null): TextView = bodyText(text).apply {
	setTextColor(getThemeColor(materialR.attr.colorOnSurfaceVariant))
	accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
}

/**
 * The shared "nothing here" / "something went wrong" state: Kitsune in [form], a [title], a [message] that says what
 * to do next and, when [actionLabel] is given, a filled button running [onAction]. Child 0 is the mascot.
 */
fun Context.emptyState(
	form: MascotForm,
	title: CharSequence?,
	message: CharSequence,
	actionLabel: String? = null,
	onAction: () -> Unit = {},
): LinearLayout = LinearLayout(this).apply {
	orientation = LinearLayout.VERTICAL
	gravity = Gravity.CENTER_HORIZONTAL
	val pad = dp(24)
	setPadding(pad, pad, pad, pad)
	addView(ImageView(context).apply {
		adjustViewBounds = true
		scaleType = ImageView.ScaleType.FIT_CENTER
		Mascot.bind(this, form)
	}, LinearLayout.LayoutParams(dp(120), dp(120)))
	if (!title.isNullOrEmpty()) {
		addView(TextView(context).apply {
			text = title
			gravity = Gravity.CENTER_HORIZONTAL
			setThemeTextAppearance(materialR.attr.textAppearanceTitleMedium, materialR.style.TextAppearance_Material3_TitleMedium)
			ViewCompat.setAccessibilityHeading(this, true)
			setPadding(0, dp(12), 0, 0)
		}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	}
	addView(bodyText(message).apply {
		gravity = Gravity.CENTER_HORIZONTAL
		setTextColor(getThemeColor(materialR.attr.colorOnSurfaceVariant))
		setPadding(0, dp(if (title.isNullOrEmpty()) 12 else 4), 0, 0)
	}, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
	if (actionLabel != null) {
		addView(primaryButton(actionLabel, onAction), LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.WRAP_CONTENT,
			ViewGroup.LayoutParams.WRAP_CONTENT,
		).apply { topMargin = dp(16) })
	}
}

/** Kitsune in [form] above a centred [message], for empty lists. */
fun Context.emptyState(form: MascotForm, message: CharSequence): LinearLayout = emptyState(form, null, message)
