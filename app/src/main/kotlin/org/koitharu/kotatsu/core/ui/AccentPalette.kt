package org.koitharu.kotatsu.core.ui

import kotlin.math.pow

/** Accent colour presets and colour maths (pure, unit-tested). Material generates the full light/dark scheme from a seed. */
internal object AccentPalette {

	/** [seed] is an opaque ARGB colour, or null for "no accent" (the chosen theme / system dynamic colour). */
	data class Preset(val key: String, val seed: Int?)

	val presets = listOf(
		Preset("system", null),
		Preset("ocean_teal", 0xFF00796B.toInt()),
		Preset("vaporwave", 0xFF9B5DE5.toInt()),
		Preset("pastel", 0xFFB5A8F2.toInt()),
		Preset("monochrome", 0xFF6B6B6B.toInt()),
	)

	/** Accepts "#00796B", "00796b" and short "#0f8"; returns an opaque colour or null. */
	fun parseHex(text: String): Int? {
		val digits = text.trim().removePrefix("#")
		val full = when (digits.length) {
			3 -> digits.flatMap { listOf(it, it) }.joinToString("")
			6 -> digits
			else -> return null
		}
		val value = full.toLongOrNull(16) ?: return null
		return (0xFF000000L or value).toInt()
	}

	fun toHex(color: Int): String = "#%06X".format(color and 0xFFFFFF)

	/** WCAG relative luminance of an sRGB colour. */
	fun luminance(color: Int): Double {
		fun channel(shift: Int): Double {
			val c = ((color shr shift) and 0xFF) / 255.0
			return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
		}
		return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
	}

	/** WCAG contrast ratio, 1.0 (identical) to 21.0 (black on white). */
	fun contrastRatio(a: Int, b: Int): Double {
		val la = luminance(a)
		val lb = luminance(b)
		val lighter = maxOf(la, lb)
		val darker = minOf(la, lb)
		return (lighter + 0.05) / (darker + 0.05)
	}

	/** Large controls and icons need at least 3:1 against their background. */
	const val MIN_CONTROL_CONTRAST = 3.0

	fun isReadable(foreground: Int, background: Int) = contrastRatio(foreground, background) >= MIN_CONTROL_CONTRAST
}
