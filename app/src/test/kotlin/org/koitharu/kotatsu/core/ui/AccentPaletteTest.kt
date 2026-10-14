package org.koitharu.kotatsu.core.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AccentPaletteTest {

	@Test
	fun parsesHexVariants() {
		assertEquals(0xFF00796B.toInt(), AccentPalette.parseHex("#00796B"))
		assertEquals(0xFF00796B.toInt(), AccentPalette.parseHex(" 00796b "))
		assertEquals(0xFF00FF88.toInt(), AccentPalette.parseHex("#0f8"))
		assertNull(AccentPalette.parseHex("#12345"))
		assertNull(AccentPalette.parseHex("zzzzzz"))
		assertNull(AccentPalette.parseHex(""))
	}

	@Test
	fun formatsHex() {
		assertEquals("#00796B", AccentPalette.toHex(0xFF00796B.toInt()))
		assertEquals("#000000", AccentPalette.toHex(0xFF000000.toInt()))
	}

	@Test
	fun contrastMatchesWcagReferenceValues() {
		assertEquals(21.0, AccentPalette.contrastRatio(0xFF000000.toInt(), 0xFFFFFFFF.toInt()), 0.01)
		assertEquals(1.0, AccentPalette.contrastRatio(0xFF123456.toInt(), 0xFF123456.toInt()), 0.0001)
		assertEquals(4.48, AccentPalette.contrastRatio(0xFF777777.toInt(), 0xFFFFFFFF.toInt()), 0.05)
	}

	@Test
	fun readabilityThreshold() {
		assertTrue(AccentPalette.isReadable(0xFF00796B.toInt(), 0xFFFFFFFF.toInt()))
		assertFalse(AccentPalette.isReadable(0xFFFFFF99.toInt(), 0xFFFFFFFF.toInt()))
	}

	@Test
	fun presetsHaveUniqueKeysAndSystemHasNoSeed() {
		assertEquals(AccentPalette.presets.size, AccentPalette.presets.map { it.key }.toSet().size)
		assertNull(AccentPalette.presets.first { it.key == "system" }.seed)
		assertTrue(AccentPalette.presets.filter { it.key != "system" }.all { it.seed != null })
	}
}
