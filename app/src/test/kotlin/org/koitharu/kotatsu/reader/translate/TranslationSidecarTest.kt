package org.koitharu.kotatsu.reader.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koitharu.kotatsu.reader.translate.TranslationLayout.Box
import org.koitharu.kotatsu.reader.translate.TranslationLayout.Piece

class TranslationSidecarTest {

	private val meta = TranslationMeta(
		"ja", "en",
		listOf(
			TranslatedBlock(0, 0, 100, 100, "あ", "Ah", 0.9f),
			TranslatedBlock(20, 20, 60, 60, "い", "Oh", 0.4f),
		),
	)

	@Test
	fun roundTripsThroughJson() {
		assertEquals(meta, TranslationSidecar.decode(TranslationSidecar.encode(meta)))
	}

	@Test
	fun olderCachedPagesLoadWithDefaultFontAndChoiceSurvivesRewrite() {
		val older = """{"sourceLanguage":"ja","targetLanguage":"en","blocks":[]}"""
		assertEquals("sans", TranslationSidecar.decode(older)?.fontStyle)
		val styled = meta.copy(fontStyle = "serif")
		assertEquals("serif", TranslationSidecar.decode(TranslationSidecar.encode(styled))?.fontStyle)
	}

	@Test
	fun garbageDecodesToNull() {
		assertNull(TranslationSidecar.decode("not json"))
	}

	@Test
	fun tapPicksTheSmallestOverlappingBlock() {
		assertEquals(1, TranslationSidecar.blockAt(meta.blocks, 30f, 30f))
		assertEquals(0, TranslationSidecar.blockAt(meta.blocks, 90f, 90f))
		assertNull(TranslationSidecar.blockAt(meta.blocks, 500f, 500f))
	}

	@Test
	fun correctionMarksBlockAndIgnoresBlank() {
		val fixed = TranslationSidecar.withCorrection(meta, 1, "  Hey  ")
		assertEquals("Hey", fixed.blocks[1].translation)
		assertTrue(fixed.blocks[1].corrected)
		assertFalse(TranslationSidecar.isDoubtful(fixed.blocks[1]))
		assertEquals(meta, TranslationSidecar.withCorrection(meta, 1, "   "))
		assertEquals(meta, TranslationSidecar.withCorrection(meta, 9, "x"))
	}

	@Test
	fun lowConfidenceIsDoubtful() {
		assertTrue(TranslationSidecar.isDoubtful(meta.blocks[1]))
		assertFalse(TranslationSidecar.isDoubtful(meta.blocks[0]))
		assertFalse(TranslationSidecar.isDoubtful(meta.blocks[0].copy(confidence = -1f)))
		assertEquals(40, TranslationSidecar.confidencePercent(meta.blocks[1]))
		assertNull(TranslationSidecar.confidencePercent(meta.blocks[0].copy(confidence = -1f)))
	}

	@Test
	fun averageIgnoresUnknown() {
		assertEquals(-1f, TranslationSidecar.averageConfidence(listOf(-1f)), 0f)
		assertEquals(0.5f, TranslationSidecar.averageConfidence(listOf(0.4f, 0.6f, -1f)), 0.001f)
	}

	@Test
	fun nearbyColumnsMergeRightToLeftForCjk() {
		val pieces = listOf(
			Piece(Box(100, 100, 130, 300), "二", 0.9f),
			Piece(Box(140, 100, 170, 300), "一", 0.5f),
			Piece(Box(800, 900, 830, 1000), "far", 0.8f),
		)
		val grouped = TranslationLayout.groupPieces(pieces, isCjk = true)
		assertEquals(2, grouped.size)
		assertEquals("一二", grouped[0].text)
		assertEquals(Box(100, 100, 170, 300), grouped[0].box)
		assertEquals(0.5f, grouped[0].confidence, 0f)
	}

	@Test
	fun latinLinesMergeTopToBottom() {
		val pieces = listOf(
			Piece(Box(50, 140, 250, 170), "WORLD"),
			Piece(Box(60, 100, 240, 130), "HELLO"),
		)
		val grouped = TranslationLayout.groupPieces(pieces, isCjk = false)
		assertEquals(1, grouped.size)
		assertEquals("HELLO WORLD", grouped[0].text)
	}

	@Test
	fun distantPiecesStaySeparate() {
		val pieces = listOf(Piece(Box(0, 0, 50, 50), "a"), Piece(Box(400, 400, 450, 450), "b"))
		assertEquals(2, TranslationLayout.groupPieces(pieces, isCjk = false).size)
	}

	@Test
	fun growToFitStaysInsideThePage() {
		val box = Box(10, 100, 200, 200)
		val grown = TranslationLayout.growToFit(box, neededHeight = 130, imageHeight = 1000)
		assertEquals(130, grown.height)
		assertEquals(box, TranslationLayout.growToFit(box, neededHeight = 80, imageHeight = 1000))
		val capped = TranslationLayout.growToFit(box, neededHeight = 1000, imageHeight = 1000)
		assertEquals(150, capped.height)
		val atBottom = TranslationLayout.growToFit(Box(0, 900, 10, 1000), 140, 1000)
		assertEquals(1000, atBottom.bottom)
		assertEquals(140, atBottom.height)
	}
}
