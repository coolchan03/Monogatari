package org.koitharu.kotatsu.stats.ui.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.core.graphics.ColorUtils
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import com.google.android.material.R as materialR

/** Compact activity heatmap; day/hour values are provided in chronological order. */
internal class ReadingHeatmapView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val activeColor = context.getThemeColor(materialR.attr.colorPrimary)
    private val density = resources.displayMetrics.density
    private var amounts: List<Long> = emptyList()
    private var columns = 7

    fun setData(values: List<Long>, columns: Int) {
        require(columns in 1..24)
        amounts = values.map { it.coerceAtLeast(0L) }
        this.columns = columns
        requestLayout()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val rows = (amounts.size + columns - 1) / columns
        val desiredHeight = (rows * 30 * density).toInt() + paddingTop + paddingBottom
        setMeasuredDimension(
            resolveSize(suggestedMinimumWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (amounts.isEmpty()) return
        val rows = (amounts.size + columns - 1) / columns
        val availableWidth = (width - paddingLeft - paddingRight).toFloat()
        val availableHeight = (height - paddingTop - paddingBottom).toFloat()
        if (availableWidth <= 0f || availableHeight <= 0f) return
        val gutter = 4f * density
        val tileWidth = (availableWidth - gutter * (columns - 1)) / columns
        val tileHeight = (availableHeight - gutter * (rows - 1)) / rows
        val maximum = amounts.maxOrNull()?.coerceAtLeast(1L) ?: 1L
        val corner = 4f * density
        amounts.forEachIndexed { index, duration ->
            val row = index / columns
            val col = index % columns
            val intensity = (duration.toDouble() / maximum).coerceIn(0.0, 1.0)
            val alpha = if (duration <= 0L) 36 else (80 + intensity * 175).toInt().coerceIn(0, 255)
            paint.color = ColorUtils.setAlphaComponent(activeColor, alpha)
            val x = paddingLeft + col * (tileWidth + gutter)
            val y = paddingTop + row * (tileHeight + gutter)
            canvas.drawRoundRect(x, y, x + tileWidth, y + tileHeight, corner, corner, paint)
        }
    }
}
