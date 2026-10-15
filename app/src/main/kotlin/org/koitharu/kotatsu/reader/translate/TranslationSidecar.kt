package org.koitharu.kotatsu.reader.translate

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** One recognised text region of a page with its translation. [confidence] is 0..1, or negative when unknown. */
@Serializable
internal data class TranslatedBlock(
	val left: Int,
	val top: Int,
	val right: Int,
	val bottom: Int,
	val original: String,
	val translation: String,
	val confidence: Float = -1f,
	/** True when the user typed the translation, so it is never overwritten. */
	val corrected: Boolean = false,
) {
	val box: TranslationLayout.Box get() = TranslationLayout.Box(left, top, right, bottom)
}

/** Everything needed to redraw or correct a translated page without running OCR again. */
@Serializable
internal data class TranslationMeta(
	val sourceLanguage: String,
	val targetLanguage: String,
	val blocks: List<TranslatedBlock>,
)

/** Pure helpers for the per-page translation record (unit-tested). */
internal object TranslationSidecar {

	/** Blocks under this confidence are flagged as doubtful in the viewer. */
	const val LOW_CONFIDENCE = 0.6f

	private val json = Json { ignoreUnknownKeys = true }

	fun encode(meta: TranslationMeta): String = json.encodeToString(meta)

	fun decode(text: String): TranslationMeta? = runCatching { json.decodeFromString<TranslationMeta>(text) }.getOrNull()

	/** The block under a tap at ([x], [y]) in page pixels, preferring the smallest when blocks overlap. */
	fun blockAt(blocks: List<TranslatedBlock>, x: Float, y: Float): Int? =
		blocks.withIndex()
			.filter { (_, b) -> x >= b.left && x <= b.right && y >= b.top && y <= b.bottom }
			.minByOrNull { (_, b) -> (b.right - b.left).toLong() * (b.bottom - b.top) }
			?.index

	/** Replaces the translation of block [index] with the user's own text. Blank text restores nothing and is ignored. */
	fun withCorrection(meta: TranslationMeta, index: Int, text: String): TranslationMeta {
		val clean = text.trim()
		if (clean.isEmpty() || index !in meta.blocks.indices) return meta
		return meta.copy(
			blocks = meta.blocks.mapIndexed { i, b ->
				if (i == index) b.copy(translation = clean, corrected = true, confidence = 1f) else b
			},
		)
	}

	fun isDoubtful(block: TranslatedBlock): Boolean =
		!block.corrected && block.confidence >= 0f && block.confidence < LOW_CONFIDENCE

	/** Whole-percent text for a block's confidence, or null if unknown. */
	fun confidencePercent(block: TranslatedBlock): Int? =
		if (block.confidence < 0f) null else (block.confidence.coerceAtMost(1f) * 100f).toInt()

	/** Mean of the known confidences of OCR lines, or negative when none is known. */
	fun averageConfidence(values: List<Float>): Float {
		val known = values.filter { it >= 0f }
		return if (known.isEmpty()) -1f else known.average().toFloat()
	}
}
