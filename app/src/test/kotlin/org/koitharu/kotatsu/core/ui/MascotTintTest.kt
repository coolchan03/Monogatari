package org.koitharu.kotatsu.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MascotTintTest {

	private val teal = 0xFF1FA89A.toInt()
	private val white = 0xFFFAFAFA.toInt()
	private val gold = 0xFFE0B040.toInt()

	@Test
	fun hsvRoundTripKeepsColours() {
		listOf(teal, white, gold, 0xFF7452AA.toInt(), 0x80000000.toInt()).forEach { colour ->
			val back = MascotTint.hsvToRgb(colour ushr 24, MascotTint.rgbToHsv(colour))
			assertEquals(Integer.toHexString(colour), Integer.toHexString(back))
		}
	}

	@Test
	fun tealTakesThePrimaryHueAndOtherColoursStay() {
		val purple = 0xFF7452AA.toInt()
		val tint = MascotTint(purple, night = false)
		val hue = MascotTint.rgbToHsv(tint.apply(teal))[0]
		assertEquals(MascotTint.rgbToHsv(purple)[0], hue, 1.5f)
		assertEquals(white, tint.apply(white))
		assertEquals(gold, tint.apply(gold))
	}

	@Test
	fun greyPrimaryMakesTealNeutralInsteadOfRed() {
		val tinted = MascotTint(0xFF000000.toInt(), night = false).apply(teal)
		assertTrue(MascotTint.rgbToHsv(tinted)[1] < 0.15f)
	}

	@Test
	fun darkThemesDimBrightFurOnly() {
		val tint = MascotTint(0xFF7452AA.toInt(), night = true)
		assertTrue(MascotTint.rgbToHsv(tint.apply(white))[2] < 0.9f)
		assertEquals(gold, tint.apply(gold))
		val transparent = 0x00FFFFFF
		assertEquals(transparent, tint.apply(transparent))
	}
}
