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
			val background = sampleBackground(source, covered)
			fill.color = background
			val corner = pad * 2f
			canvas.drawRoundRect(covered.toRectF(), corner, corner, fill)
			val target = TranslationLayout.widenForHorizontalText(covered, width, height)
			if (target != covered) {
				canvas.drawRoundRect(target.toRectF(), corner, corner, fill)
			}
			textPaint.color = TranslationLayout.contrastingTextColor(background)
			drawFittedText(canvas, block.translation, target, pad, textPaint)
		}
		return out
	}

	private fun drawFittedText(canvas: Canvas, text: String, box: TranslationLayout.Box, pad: Int, paint: TextPaint) {
		val innerWidth = (box.width - pad * 2).coerceAtLeast(1)
		val innerHeight = (box.height - pad * 2).coerceAtLeast(1)
		val maxSize = (innerHeight * 0.45f).coerceIn(10f, 64f)
		val minSize = 8f.coerceAtMost(maxSize)
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
			.build()

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
