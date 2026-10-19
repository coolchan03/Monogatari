package org.koitharu.kotatsu.reader.translate

import org.koitharu.kotatsu.core.util.DisplayText
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.roundToInt
import java.text.BreakIterator
import java.util.Locale

/** Paints over the original text of each block and draws its translation in the freed space. */
internal object TranslatedPageRenderer {

	private const val MIN_TEXT_SIZE = 8f

	/** Edge colours differing by more than this in any channel switch to the smooth fill. */
	private const val SMOOTH_FILL_SPREAD = 28

	fun render(source: Bitmap, meta: TranslationMeta): Bitmap =
		// Cleaning here also repairs translations saved before entities were decoded.
		render(source, meta.blocks.map { Block(it.box, DisplayText.clean(it.translation), meta.targetLanguage, meta.fontStyle) })

	class Block(val box: TranslationLayout.Box, val translation: String, val targetLanguage: String? = null, val fontStyle: String = "sans")

	private data class BubbleArea(val content: TranslationLayout.Box, val outline: Path)

	fun render(source: Bitmap, blocks: List<Block>): Bitmap {
		val out = source.copy(Bitmap.Config.ARGB_8888, true)
		val canvas = Canvas(out)
		val width = out.width
		val height = out.height
		val pad = max(2, (minOf(width, height) * 0.006f).roundToInt())
		val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
		val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
			typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
		}
		for (block in blocks) {
			if (block.translation.isBlank()) continue
			textPaint.typeface = when (block.fontStyle) {
				"serif" -> Typeface.create(Typeface.SERIF, Typeface.NORMAL)
				"comic" -> Typeface.create("casual", Typeface.BOLD)
				else -> Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
			}
			val covered = TranslationLayout.inflate(block.box, pad, width, height)
			val edges = sampleEdges(source, covered)
			val background = TranslationLayout.medianColor(edges)
			fill.color = background
			val corner = pad * 2f
			// Shaded or gradient bubbles get a smooth fill from their edges; plain ones keep the flat colour.
			val smooth = TranslationLayout.colorSpread(edges) > SMOOTH_FILL_SPREAD
			fun paint(region: TranslationLayout.Box) {
				if (smooth) fillSmooth(canvas, region, edges) else canvas.drawRoundRect(region.toRectF(), corner, corner, fill)
			}
			// Detect the connected paper boundary before painting; do not erase artwork outside irregular bubbles.
			val bubble = findBubble(source, covered, background, pad)
			if (bubble != null) {
				canvas.save()
				canvas.clipPath(bubble.outline)
				paint(covered)
				canvas.restore()
			} else paint(covered)
			// Never erase the bounding rectangle of an irregular bubble: this covers its outline and panel art.
			val target = if (bubble != null) bubble.content else {
				val widened = TranslationLayout.widenForHorizontalText(covered, width, height)
				growIfNeeded(block.translation, widened, pad, height, textPaint)
			}
			if (bubble == null && target != covered) paint(target)
			textPaint.color = TranslationLayout.contrastingTextColor(background)
			drawFittedText(canvas, block.translation, target, pad, textPaint, bubble?.outline, block.targetLanguage)
		}
		return out
	}

	/** Text that cannot fit even at the smallest size gets extra room above/below instead of being clipped. */
	private fun growIfNeeded(text: String, box: TranslationLayout.Box, pad: Int, imageHeight: Int, paint: TextPaint): TranslationLayout.Box {
		paint.textSize = MIN_TEXT_SIZE
		val needed = layout(text, paint, (box.width - pad * 2).coerceAtLeast(1)).height + pad * 2
		return TranslationLayout.growToFit(box, needed, imageHeight)
	}

	private fun drawFittedText(canvas: Canvas, text: String, box: TranslationLayout.Box, pad: Int, paint: TextPaint, outline: Path? = null, language: String? = null) {
		if (TranslationLayout.prefersVerticalText(language, text, box) && drawVerticalText(canvas, text, box, pad, paint, outline, language!!)) return
		val innerWidth = (box.width - pad * 2).coerceAtLeast(1)
		val innerHeight = (box.height - pad * 2).coerceAtLeast(1)
		val maxSize = (innerHeight * 0.45f).coerceIn(10f, 64f)
		val minSize = MIN_TEXT_SIZE.coerceAtMost(maxSize)
		val size = TranslationLayout.fitTextSize(minSize, maxSize, innerHeight) { candidate ->
			paint.textSize = candidate
			layout(text, paint, innerWidth, language).height
		}
		paint.textSize = size
		val layout = layout(text, paint, innerWidth, language)
		canvas.save()
		canvas.clipRect(box.left, box.top, box.right, box.bottom)
		if (outline != null) canvas.clipPath(outline)
		val top = box.top + pad + ((innerHeight - layout.height) / 2f).coerceAtLeast(0f)
		canvas.translate((box.left + pad).toFloat(), top)
		layout.draw(canvas)
		canvas.restore()
	}

	/** Vertical CJK columns, right-to-left; grapheme boundaries keep combining marks with their glyph. */
	private fun drawVerticalText(
		canvas: Canvas, text: String, box: TranslationLayout.Box, pad: Int,
		paint: TextPaint, outline: Path?, language: String,
	): Boolean {
		val iterator = BreakIterator.getCharacterInstance(Locale.forLanguageTag(language))
		iterator.setText(text)
		val glyphs = ArrayList<String>()
		var start = iterator.first()
		var end = iterator.next()
		while (end != BreakIterator.DONE) {
			val glyph = text.substring(start, end)
			if (glyph.isNotBlank()) glyphs += glyph
			start = end
			end = iterator.next()
		}
		if (glyphs.isEmpty()) return false
		val innerWidth = box.width - pad * 2
		val innerHeight = box.height - pad * 2
		if (innerWidth <= 0 || innerHeight <= 0) return false
		val selected = (48 downTo MIN_TEXT_SIZE.toInt()).firstNotNullOfOrNull { candidate ->
			val step = candidate * 1.2f
			val rows = (innerHeight / step).toInt()
			if (rows < 1) return@firstNotNullOfOrNull null
			val columns = (glyphs.size + rows - 1) / rows
			if (columns * step <= innerWidth) Triple(candidate.toFloat(), rows, columns) else null
		} ?: return false
		val (size, rows, columns) = selected
		val step = size * 1.2f
		val oldTypeface = paint.typeface
		val oldAlign = paint.textAlign
		// Preserve the user's font choice; Android falls back to an installed CJK glyph face where needed.
		paint.textAlign = Paint.Align.CENTER
		paint.textSize = size
		val xRight = box.left + pad + (innerWidth + columns * step) / 2f - step / 2f
		val yTop = box.top + pad + (innerHeight - minOf(rows, glyphs.size) * step) / 2f
		canvas.save()
		canvas.clipRect(box.left, box.top, box.right, box.bottom)
		if (outline != null) canvas.clipPath(outline)
		glyphs.forEachIndexed { i, glyph ->
			val column = i / rows
			val row = i % rows
			val x = xRight - column * step
			if (TranslationLayout.rotatesInVerticalText(glyph)) {
				// Turn the long vowel mark, dashes and tildes to run along the column.
				val centerY = yTop + (row + 0.5f) * step
				canvas.save()
				canvas.rotate(90f, x, centerY)
				canvas.drawText(glyph, x, centerY - (paint.descent() + paint.ascent()) / 2f, paint)
				canvas.restore()
			} else {
				canvas.drawText(TranslationLayout.verticalForm(glyph), x, yTop + (row + 0.82f) * step, paint)
			}
		}
		canvas.restore()
		paint.typeface = oldTypeface
		paint.textAlign = oldAlign
		return true
	}

	private fun layout(text: String, paint: TextPaint, width: Int, language: String? = null): StaticLayout =
		StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
			.setTextDirection(if (TranslationLayout.isRtlLanguage(language)) TextDirectionHeuristics.FIRSTSTRONG_RTL else TextDirectionHeuristics.FIRSTSTRONG_LTR)
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.setLineSpacing(0f, 1.0f)
			.setIncludePad(false)
			.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
			.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
			.build()

	/** The bubble interior around [covered], shrunk a little off its outline, or null when none can be told apart. */
	private fun findBubble(source: Bitmap, covered: TranslationLayout.Box, paper: Int, pad: Int): BubbleArea? {
		val reach = (max(covered.width, covered.height) / 2).coerceAtLeast(pad * 4)
		val window = TranslationLayout.inflate(covered, reach, source.width, source.height)
		val w = window.width
		val h = window.height
		if (w <= 0 || h <= 0 || w.toLong() * h > 2_500_000L) return null
		val pixels = IntArray(w * h)
		source.getPixels(pixels, 0, w, window.left, window.top, w, h)
		val local = TranslationLayout.Box(covered.left - window.left, covered.top - window.top, covered.right - window.left, covered.bottom - window.top)
		val found = BubbleFinder.interior(pixels, w, h, BubbleFinder.seedsAround(local, 2), paper) ?: return null
		val bounds = TranslationLayout.shrink(
			TranslationLayout.Box(found.bounds.left + window.left, found.bounds.top + window.top,
				found.bounds.right + window.left, found.bounds.bottom + window.top), pad * 2)
		if (bounds.width <= covered.width || bounds.height <= 0) return null
		// Lay text out in the largest rectangle that really fits inside the bubble shape, not its bounding box,
		// so an oval bubble's outline does not clip the first and last lines. Never smaller than the original text area.
		val (firsts, lasts) = BubbleFinder.rowSpans(found)
		val inscribed = BubbleFinder.largestInscribed(firsts, lasts)?.let { r ->
			TranslationLayout.shrink(TranslationLayout.Box(
				window.left + found.bounds.left + r.left, window.top + found.bounds.top + r.top,
				window.left + found.bounds.left + r.right, window.top + found.bounds.top + r.bottom), pad)
		}
		val inner = inscribed?.takeIf {
			it.width > 0 && it.height > 0 && it.width.toLong() * it.height >= covered.width.toLong() * covered.height
		} ?: bounds
		// A flood region can occasionally be a large panel. Reject an implausible near-full-window region.
		if (found.bounds.width.toLong() * found.bounds.height > w.toLong() * h * 85L / 100L) return null
		// Every scanline traces the connected paper region, not the enclosing rectangular OCR region.
		val outline = Path()
		for (y in found.bounds.top until found.bounds.bottom) {
			var first = w
			var last = -1
			for (x in found.bounds.left until found.bounds.right) {
				if (found.contains(x, y)) { first = minOf(first, x); last = x }
			}
			if (last >= first) outline.addRect(
				(window.left + first + 1).toFloat(), (window.top + y).toFloat(),
				(window.left + last).toFloat(), (window.top + y + 1).toFloat(), Path.Direction.CW)
		}
		return BubbleArea(inner, outline)
	}

	/** Smooth fill from the edge colours [left, right, top, bottom] (see [sampleEdges]). */
	private fun fillSmooth(canvas: Canvas, region: TranslationLayout.Box, edges: IntArray) {
		val w = region.width
		val h = region.height
		if (w <= 0 || h <= 0 || w.toLong() * h > 4_000_000L) return
		val pixels = IntArray(w * h)
		for (y in 0 until h) {
			val fy = if (h > 1) y / (h - 1f) else 0f
			for (x in 0 until w) {
				val fx = if (w > 1) x / (w - 1f) else 0f
				pixels[y * w + x] = TranslationLayout.blendEdges(edges[0], edges[1], edges[2], edges[3], fx, fy)
			}
		}
		val patch = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
		canvas.drawBitmap(patch, region.left.toFloat(), region.top.toFloat(), null)
		patch.recycle()
	}

	/** Median colours along the box's left, right, top and bottom edges, in that order. */
	private fun sampleEdges(bitmap: Bitmap, box: TranslationLayout.Box): IntArray {
		val right = (box.right - 1).coerceAtLeast(box.left)
		val bottom = (box.bottom - 1).coerceAtLeast(box.top)
		val steps = 12
		val left = IntArray(steps + 1)
		val rightEdge = IntArray(steps + 1)
		val top = IntArray(steps + 1)
		val bottomEdge = IntArray(steps + 1)
		for (i in 0..steps) {
			val x = box.left + (right - box.left) * i / steps
			val y = box.top + (bottom - box.top) * i / steps
			left[i] = bitmap.getPixel(box.left, y)
			rightEdge[i] = bitmap.getPixel(right, y)
			top[i] = bitmap.getPixel(x, box.top)
			bottomEdge[i] = bitmap.getPixel(x, bottom)
		}
		return intArrayOf(
			TranslationLayout.medianColor(left), TranslationLayout.medianColor(rightEdge),
			TranslationLayout.medianColor(top), TranslationLayout.medianColor(bottomEdge),
		)
	}

	/** Median colour of pixels just outside the box: usually the bubble's paper colour. */
	private fun sampleBackground(bitmap: Bitmap, box: TranslationLayout.Box): Int {
		val samples = ArrayList<Int>(96)
		val right = (box.right - 1).coerceAtLeast(box.left)
		val bottom = (box.bottom - 1).coerceAtLeast(box.top)
		val steps = 24
		for (i in 0..steps) {
			val x = box.left + (right - box.left) * i / steps
			val y = box.top + (bottom - box.top) * i / steps
			samples += bitmap.getPixel(x, box.top)
			samples += bitmap.getPixel(x, bottom)
			samples += bitmap.getPixel(box.left, y)
			samples += bitmap.getPixel(right, y)
		}
		return TranslationLayout.medianColor(samples.toIntArray())
	}

	private fun TranslationLayout.Box.toRectF() = RectF(left.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat())
}
