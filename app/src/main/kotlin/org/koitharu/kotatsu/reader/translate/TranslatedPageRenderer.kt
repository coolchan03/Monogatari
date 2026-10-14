package org.koitharu.kotatsu.reader.translate

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.roundToInt

/** Paints over the original text of each block and draws its translation in the freed space. */
internal object TranslatedPageRenderer {

	private const val MIN_TEXT_SIZE = 8f

	/** Edge colours differing by more than this in any channel switch to the smooth fill. */
	private const val SMOOTH_FILL_SPREAD = 28

	fun render(source: Bitmap, meta: TranslationMeta): Bitmap =
		render(source, meta.blocks.map { Block(it.box, it.translation) })

	class Block(val box: TranslationLayout.Box, val translation: String)

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
			paint(covered)
			val widened = TranslationLayout.widenForHorizontalText(covered, width, height)
			val target = growIfNeeded(block.translation, widened, pad, height, textPaint)
			if (target != covered) {
				paint(target)
			}
			textPaint.color = TranslationLayout.contrastingTextColor(background)
			drawFittedText(canvas, block.translation, target, pad, textPaint)
		}
		return out
	}

	/** Text that cannot fit even at the smallest size gets extra room above/below instead of being clipped. */
	private fun growIfNeeded(text: String, box: TranslationLayout.Box, pad: Int, imageHeight: Int, paint: TextPaint): TranslationLayout.Box {
		paint.textSize = MIN_TEXT_SIZE
		val needed = layout(text, paint, (box.width - pad * 2).coerceAtLeast(1)).height + pad * 2
		return TranslationLayout.growToFit(box, needed, imageHeight)
	}

	private fun drawFittedText(canvas: Canvas, text: String, box: TranslationLayout.Box, pad: Int, paint: TextPaint) {
		val innerWidth = (box.width - pad * 2).coerceAtLeast(1)
		val innerHeight = (box.height - pad * 2).coerceAtLeast(1)
		val maxSize = (innerHeight * 0.45f).coerceIn(10f, 64f)
		val minSize = MIN_TEXT_SIZE.coerceAtMost(maxSize)
		val size = TranslationLayout.fitTextSize(minSize, maxSize, innerHeight) { candidate ->
			paint.textSize = candidate
			layout(text, paint, innerWidth).height
		}
		paint.textSize = size
		val layout = layout(text, paint, innerWidth)
		canvas.save()
		canvas.clipRect(box.left, box.top, box.right, box.bottom)
		val top = box.top + pad + ((innerHeight - layout.height) / 2f).coerceAtLeast(0f)
		canvas.translate((box.left + pad).toFloat(), top)
		layout.draw(canvas)
		canvas.restore()
	}

	private fun layout(text: String, paint: TextPaint, width: Int): StaticLayout =
		StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
			.setAlignment(Layout.Alignment.ALIGN_CENTER)
			.setLineSpacing(0f, 1.0f)
			.setIncludePad(false)
			.setBreakStrategy(Layout.BREAK_STRATEGY_BALANCED)
			.setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
			.build()

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
