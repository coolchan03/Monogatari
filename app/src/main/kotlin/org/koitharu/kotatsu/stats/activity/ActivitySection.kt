package org.koitharu.kotatsu.stats.activity

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaAccents
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.bodyText
import org.koitharu.kotatsu.core.ui.dp
import org.koitharu.kotatsu.core.ui.headerCard
import org.koitharu.kotatsu.core.ui.sectionCard
import org.koitharu.kotatsu.core.ui.statusText
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.isNightMode
import org.koitharu.kotatsu.core.util.ext.setThemeTextAppearance
import com.google.android.material.R as materialR

/**
 * The "Activity" card at the top of the Stats screen: streaks, this week / this month per medium and a
 * 30-day chart. Collapsed it is two lines; tapping expands it. Shows the mascot when nothing was recorded.
 */
class ActivitySection(private val host: FrameLayout) {

	private var expanded = false
	private var summary: ActivitySummary? = null

	fun bind(summary: ActivitySummary) {
		this.summary = summary
		render()
	}

	private fun render() {
		val data = summary ?: return
		val context = host.context
		host.removeAllViews()
		if (!data.hasData) {
			val (card, _) = context.headerCard(
				MascotForm.DEFAULT,
				context.getString(R.string.stats_activity_empty_title),
				context.getString(R.string.stats_activity_empty_message),
				mascotSizeDp = 48,
			)
			host.addView(card, matchWidth())
			return
		}
		val (card, column) = context.sectionCard(context.getString(R.string.stats_activity_title))
		column.addView(
			context.bodyText(streakText(context, data.currentStreak)).apply {
				setThemeTextAppearance(materialR.attr.textAppearanceTitleMedium, materialR.style.TextAppearance_Material3_TitleMedium)
			},
			matchWidth(),
		)
		column.addPeriod(context.getString(R.string.stats_activity_this_week), data.week)
		if (expanded) {
			column.addPeriod(context.getString(R.string.stats_activity_this_month), data.month)
			column.addView(
				context.statusText(
					context.resources.getQuantityString(
						R.plurals.stats_activity_longest_streak, data.longestStreak, data.longestStreak,
					),
				).apply { setPadding(0, context.dp(12), 0, 0) },
				matchWidth(),
			)
			column.addView(
				context.statusText(context.getString(R.string.stats_activity_last_30_days))
					.apply { setPadding(0, context.dp(12), 0, context.dp(4)) },
				matchWidth(),
			)
			column.addView(DailyBarsView(context).apply { setDays(data.last30Days) }, LinearLayout.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT, context.dp(56),
			))
		}
		column.addView(
			context.statusText(
				context.getString(if (expanded) R.string.stats_activity_hide_details else R.string.stats_activity_show_details),
			).apply { setPadding(0, context.dp(12), 0, 0) },
			matchWidth(),
		)
		card.setOnClickListener {
			expanded = !expanded
			render()
		}
		host.addView(card, matchWidth())
	}

	private fun LinearLayout.addPeriod(title: String, totals: Map<String, Int>) {
		val ctx = context
		val total = totals.values.sum()
		addView(
			ctx.statusText("$title: ${formatDuration(ctx, total)}").apply { setPadding(0, ctx.dp(12), 0, ctx.dp(6)) },
			matchWidth(),
		)
		addView(StackedBarView(ctx).apply { setValues(totals) }, LinearLayout.LayoutParams(
			ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(10),
		))
		if (expanded) {
			for (medium in ActivityStats.MEDIA) {
				val seconds = totals[medium] ?: 0
				if (seconds <= 0) continue
				addView(legendLine(ctx, medium, seconds), matchWidth().apply { topMargin = ctx.dp(4) })
			}
		}
	}

	private fun legendLine(context: Context, medium: String, seconds: Int): TextView {
		val text = SpannableStringBuilder("● ")
		text.setSpan(ForegroundColorSpan(accentColor(context, medium)), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
		text.append(mediumLabel(context, medium)).append(": ").append(formatDuration(context, seconds))
		return context.bodyText(text)
	}

	private fun matchWidth() = LinearLayout.LayoutParams(
		ViewGroup.LayoutParams.MATCH_PARENT,
		ViewGroup.LayoutParams.WRAP_CONTENT,
	)

	companion object {

		fun streakText(context: Context, days: Int): String =
			if (days <= 0) {
				context.getString(R.string.stats_activity_no_streak)
			} else {
				context.resources.getQuantityString(R.plurals.stats_activity_days_in_a_row, days, days)
			}

		fun mediumLabel(context: Context, medium: String): String = context.getString(
			when (medium) {
				ActivityStats.MANGA -> R.string.stats_activity_manga
				ActivityStats.BOOK -> R.string.stats_activity_books
				ActivityStats.VIDEO -> R.string.stats_activity_video
				else -> R.string.stats_activity_audio
			},
		)

		fun formatDuration(context: Context, seconds: Int): String {
			val minutes = seconds.coerceAtLeast(0) / 60
			return if (minutes >= 60) {
				context.getString(R.string.stats_activity_hours_minutes, minutes / 60, minutes % 60)
			} else {
				context.getString(R.string.stats_activity_minutes, minutes)
			}
		}

		/** The media accent, lightened on dark themes so it stays visible on dark cards. */
		fun accentColor(context: Context, medium: String): Int {
			val color = MediaAccents.color(medium)
			return if (context.resources.isNightMode) ColorUtils.blendARGB(color, Color.WHITE, 0.35f) else color
		}
	}
}

/** One horizontal bar split into the media of a period, proportional to the seconds in each. */
private class StackedBarView(context: Context) : View(context) {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val rect = RectF()
	private val clip = Path()
	private val trackColor = context.getThemeColor(materialR.attr.colorSurfaceVariant)
	private var segments: List<Pair<Int, Int>> = emptyList()

	fun setValues(totals: Map<String, Int>) {
		segments = ActivityStats.MEDIA.mapNotNull { medium ->
			val seconds = totals[medium] ?: 0
			if (seconds > 0) ActivitySection.accentColor(context, medium) to seconds else null
		}
		importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		val w = width.toFloat()
		val h = height.toFloat()
		rect.set(0f, 0f, w, h)
		paint.color = trackColor
		canvas.drawRoundRect(rect, h / 2f, h / 2f, paint)
		val total = segments.sumOf { it.second }
		if (total <= 0) return
		clip.reset()
		clip.addRoundRect(rect, h / 2f, h / 2f, Path.Direction.CW)
		canvas.save()
		canvas.clipPath(clip)
		var x = 0f
		for ((color, seconds) in segments) {
			val segmentWidth = w * seconds / total
			paint.color = color
			canvas.drawRect(x, 0f, x + segmentWidth, h, paint)
			x += segmentWidth
		}
		canvas.restore()
	}
}

/** The last days as thin stacked columns (one per day, oldest first), scaled to the busiest day. */
private class DailyBarsView(context: Context) : View(context) {

	private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
	private val baseline = context.getThemeColor(materialR.attr.colorSurfaceVariant)
	private var days: List<Map<String, Int>> = emptyList()

	fun setDays(days: List<Map<String, Int>>) {
		this.days = days
		val total = days.sumOf { ActivityStats.totalSeconds(it) }
		contentDescription = context.getString(
			R.string.stats_activity_chart_description,
			days.size,
			ActivitySection.formatDuration(context, total),
		)
		invalidate()
	}

	override fun onDraw(canvas: Canvas) {
		if (days.isEmpty()) return
		val w = width.toFloat()
		val h = height.toFloat()
		val gap = context.dp(2).toFloat()
		val barWidth = ((w - gap * (days.size - 1)) / days.size).coerceAtLeast(1f)
		val peak = days.maxOf { ActivityStats.totalSeconds(it) }.coerceAtLeast(1)
		val minHeight = context.dp(2).toFloat()
		for ((index, day) in days.withIndex()) {
			val left = index * (barWidth + gap)
			val total = ActivityStats.totalSeconds(day)
			if (total <= 0) {
				paint.color = baseline
				canvas.drawRect(left, h - minHeight, left + barWidth, h, paint)
				continue
			}
			val barHeight = (h * total / peak).coerceAtLeast(minHeight * 2)
			var bottom = h
			for (medium in ActivityStats.MEDIA) {
				val seconds = day[medium] ?: 0
				if (seconds <= 0) continue
				val part = barHeight * seconds / total
				paint.color = ActivitySection.accentColor(context, medium)
				canvas.drawRect(left, bottom - part, left + barWidth, bottom, paint)
				bottom -= part
			}
		}
	}
}
