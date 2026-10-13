package org.koitharu.kotatsu.core.ui.image

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import kotlin.math.min

/** Deterministic offline cover for missing artwork; draws only locally supplied title text. */
class GeneratedCoverDrawable(private val title: String, private val author: String? = null) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var alphaValue = 255
    private val palette = intArrayOf(0xFF204B54.toInt(), 0xFF38456C.toInt(),
        0xFF675064.toInt(), 0xFF4A614A.toInt(), 0xFF755B40.toInt())

    override fun draw(canvas: Canvas) {
        val rect = bounds
        if (rect.isEmpty) return
        paint.color = palette[(title.hashCode().toLong() and Long.MAX_VALUE).rem(palette.size).toInt()]
        paint.alpha = alphaValue
        canvas.drawRect(rect, paint)
        paint.color = Color.WHITE
        paint.alpha = alphaValue
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.BOLD)
        val label = title.trim().ifEmpty { "Monogatari" }
        paint.textSize = min(rect.width() * 0.12f, rect.height() * 0.065f).coerceAtLeast(12f)
        val maxWidth = rect.width() * 0.82f
        val words = label.split(Regex("\\s+"))
        val lines = mutableListOf<String>()
        var line = ""
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                lines += line
                line = word
                if (lines.size == 3) break
            } else {
                line = candidate
            }
        }
        if (lines.size < 3 && line.isNotEmpty()) lines += line
        val shown = lines.take(3)
        val spacing = paint.textSize * 1.25f
        val baseline = rect.exactCenterY() - (shown.size - 1) * spacing / 2f
        shown.forEachIndexed { index, text ->
            val safeText = if (paint.measureText(text) > maxWidth)
                text.takeWhileIndexedWidth(maxWidth) else text
            canvas.drawText(safeText, rect.exactCenterX(), baseline + spacing * index, paint)
        }
        author?.trim()?.takeIf { it.isNotEmpty() }?.let { byline ->
            paint.typeface = android.graphics.Typeface.DEFAULT
            paint.textSize = min(rect.width() * 0.07f, rect.height() * 0.035f).coerceAtLeast(10f)
            val safeByline = if (paint.measureText(byline) > maxWidth)
                byline.takeWhileIndexedWidth(maxWidth) else byline
            canvas.drawText(safeByline, rect.exactCenterX(), rect.bottom - rect.height() * 0.1f, paint)
        }
    }

    private fun String.takeWhileIndexedWidth(max: Float): String {
        var count = length
        while (count > 1 && paint.measureText(take(count) + "…") > max) count--
        return take(count) + if (count < length) "…" else ""
    }

    override fun setAlpha(alpha: Int) { alphaValue = alpha.coerceIn(0, 255); invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Deprecated("Android Drawable API") override fun getOpacity(): Int = PixelFormat.OPAQUE
}
