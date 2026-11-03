package org.koitharu.kotatsu.core.media

import android.content.Context
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlin.math.roundToInt

/**
 * The shared "Compact view" setting of the Library and the Discover pages: more, smaller covers per row.
 * One flag for all of them, kept in its own prefs file so no existing preference is touched.
 */
object CompactView {
    const val PREFS_NAME = "monogatari_ui"
    const val KEY = "compact_view"

    /** Compact shows about this many times more columns than the default. */
    const val COLUMN_FACTOR = 1.3f

    const val DEFAULT_CELL_DP = 176f

    /** Compact titles and margins are scaled by this much. */
    const val TEXT_SCALE = 0.85f

    fun isEnabled(context: Context): Boolean = runCatching {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY, false)
    }.getOrDefault(false)

    fun setEnabled(context: Context, value: Boolean) {
        runCatching {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(KEY, value).apply()
        }
    }

    /**
     * Number of grid columns for a container [widthDp] wide whose cells are [cellDp] wide by default.
     * The default view is `widthDp / cellDp` columns within [minColumns]..[maxColumns]; compact shows
     * [COLUMN_FACTOR] times as many, and always at least one more, so the change is visible on a phone too.
     */
    fun columnsFor(
        widthDp: Float,
        compact: Boolean,
        cellDp: Float = DEFAULT_CELL_DP,
        minColumns: Int = 2,
        maxColumns: Int = 7,
    ): Int {
        val base = (widthDp / cellDp.coerceAtLeast(1f)).toInt().coerceIn(minColumns, maxColumns)
        if (!compact) return base
        val wider = maxOf(base + 1, (base * COLUMN_FACTOR).roundToInt())
        return wider.coerceAtMost(maxOf(maxColumns + 1, (maxColumns * COLUMN_FACTOR).roundToInt()))
    }

    /** A fixed card width made narrower in compact view. */
    fun cardWidth(basePx: Int, compact: Boolean): Int =
        if (compact) (basePx / COLUMN_FACTOR).roundToInt() else basePx

    /** Sets [view]'s text to its [defaultSizePx] (from [TextView.getTextSize] before any scaling), smaller if compact. */
    fun scaleText(view: TextView, defaultSizePx: Float, compact: Boolean) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, if (compact) defaultSizePx * TEXT_SCALE else defaultSizePx)
    }

    /** Sets a card's margins to [defaultPx] on every side, tighter if compact. */
    fun scaleMargins(card: View, defaultPx: Int, compact: Boolean) {
        val params = card.layoutParams as? ViewGroup.MarginLayoutParams ?: return
        val value = if (compact) defaultPx / 2 else defaultPx
        if (params.leftMargin != value || params.topMargin != value) {
            params.setMargins(value, value, value, value)
            card.layoutParams = params
        }
    }
}
