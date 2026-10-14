package org.koitharu.kotatsu.settings

import android.content.Context
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.AccentColors
import org.koitharu.kotatsu.core.ui.AccentPalette
import com.google.android.material.R as materialR

/** Accent colour picker: palette presets plus a custom colour, with a live preview that follows the light/dark mode. */
internal object AccentColorDialog {

	fun show(context: Context, settings: AppSettings) {
		val pad = (16 * context.resources.displayMetrics.density).toInt()
		val root = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad / 2, pad, 0)
		}
		if (!AccentColors.isSupported()) {
			root.addView(TextView(context).apply { setText(R.string.accent_color_unsupported) })
			MaterialAlertDialogBuilder(context)
				.setTitle(R.string.accent_color)
				.setView(root)
				.setPositiveButton(android.R.string.ok, null)
				.show()
			return
		}

		var selected: Int? = settings.accentColor
		val preview = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
		val warning = TextView(context).apply { setText(R.string.accent_low_contrast) }

		fun renderPreview() {
			preview.removeAllViews()
			val seed = selected
			val themed = if (seed == null) context else DynamicColors.wrapContextIfAvailable(context, AccentColors.options(seed))
			val surface = MaterialColors.getColor(themed, materialR.attr.colorSurface, 0)
			val onSurface = MaterialColors.getColor(themed, materialR.attr.colorOnSurface, 0)
			val primary = MaterialColors.getColor(themed, androidx.appcompat.R.attr.colorPrimary, 0)
			val box = LinearLayout(themed).apply {
				orientation = LinearLayout.VERTICAL
				setBackgroundColor(surface)
				setPadding(pad, pad, pad, pad)
			}
			box.addView(TextView(themed).apply {
				setText(R.string.accent_preview_text)
				setTextColor(onSurface)
			})
			val row = LinearLayout(themed).apply { orientation = LinearLayout.HORIZONTAL }
			row.addView(MaterialButton(themed).apply { setText(R.string.accent_preview_button) })
			row.addView(Chip(themed).apply { setText(R.string.accent_preview_chip) })
			box.addView(row)
			preview.addView(box, LinearLayout.LayoutParams(-1, -2))
			warning.visibility = if (AccentPalette.isReadable(primary, surface)) android.view.View.GONE else android.view.View.VISIBLE
		}

		val custom = EditText(context).apply {
			setHint(R.string.accent_custom_hint)
			setSingleLine(true)
			inputType = InputType.TYPE_CLASS_TEXT
			selected?.let { setText(AccentPalette.toHex(it)) }
		}
		val chips = ChipGroup(context).apply { isSingleSelection = true }
		for (preset in AccentPalette.presets) {
			chips.addView(Chip(context).apply {
				text = context.getString(titleOf(preset.key))
				isCheckable = true
				isChecked = preset.seed == selected
				setOnClickListener {
					selected = preset.seed
					custom.setText(preset.seed?.let { AccentPalette.toHex(it) }.orEmpty())
					renderPreview()
				}
			})
		}
		custom.doAfterTextChanged { text ->
			val color = AccentPalette.parseHex(text?.toString().orEmpty())
			if (color != null) {
				selected = color
				chips.clearCheck()
				renderPreview()
			}
		}

		root.addView(chips)
		root.addView(custom, LinearLayout.LayoutParams(-1, -2))
		root.addView(TextView(context).apply {
			setText(R.string.accent_preview_title)
			setPadding(0, pad / 2, 0, pad / 4)
		})
		root.addView(preview, LinearLayout.LayoutParams(-1, -2))
		root.addView(warning)
		renderPreview()

		MaterialAlertDialogBuilder(context)
			.setTitle(R.string.accent_color)
			.setView(root)
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(R.string.accent_apply) { _, _ -> settings.accentColor = selected }
			.show()
	}

	private fun titleOf(key: String): Int = when (key) {
		"ocean_teal" -> R.string.accent_palette_ocean_teal
		"vaporwave" -> R.string.accent_palette_vaporwave
		"pastel" -> R.string.accent_palette_pastel
		"monochrome" -> R.string.accent_palette_monochrome
		else -> R.string.accent_palette_system
	}
}
