package org.koitharu.kotatsu.tracker.ui.forecast

import android.os.Bundle
import android.text.format.DateUtils
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.addBodyText
import org.koitharu.kotatsu.core.ui.addHeaderCard
import org.koitharu.kotatsu.core.ui.addSectionCard
import org.koitharu.kotatsu.core.ui.bodyText
import org.koitharu.kotatsu.core.ui.dp
import org.koitharu.kotatsu.core.ui.emptyState
import org.koitharu.kotatsu.core.ui.pageColumn
import org.koitharu.kotatsu.core.ui.scrollable
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import org.koitharu.kotatsu.core.ui.statusText
import org.koitharu.kotatsu.core.util.ext.getThemeDrawable
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.tracker.domain.GetReleaseForecastUseCase
import org.koitharu.kotatsu.tracker.domain.ReleaseForecast
import javax.inject.Inject

/**
 * "Release forecast": which tracked titles are expected to update soon, grouped as due now, today, this week and
 * later. It is an estimate from how often the tracker found new chapters, and says so on screen.
 */
@AndroidEntryPoint
class ReleaseForecastActivity : AppCompatActivity() {

	@Inject
	internal lateinit var getForecast: GetReleaseForecastUseCase

	private lateinit var column: LinearLayout

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		title = getString(R.string.forecast_title)
		column = pageColumn()
		setMonogatariContent(column.scrollable(), MonogatariChrome.LIST_MAX_WIDTH_DP)
		showLoading()
		lifecycleScope.launch {
			runCatchingCancellable { getForecast() }
				.onSuccess(::render)
				.onFailure { showError(it.message) }
		}
	}

	private fun showLoading() {
		column.removeAllViews()
		column.addHeaderCard(MascotForm.DEFAULT, getString(R.string.forecast_title), getString(R.string.forecast_loading))
	}

	private fun showError(message: String?) {
		column.removeAllViews()
		column.addView(
			emptyState(
				MascotForm.SAD,
				getString(R.string.forecast_failed_title),
				message ?: getString(R.string.error_occurred),
				getString(R.string.try_again),
			) {
				showLoading()
				lifecycleScope.launch {
					runCatchingCancellable { getForecast() }.onSuccess(::render).onFailure { showError(it.message) }
				}
			},
		)
	}

	private fun render(entries: List<ReleaseForecast.Entry>) {
		column.removeAllViews()
		if (entries.isEmpty()) {
			column.addView(
				emptyState(MascotForm.DEFAULT, getString(R.string.forecast_empty_title), getString(R.string.forecast_empty_message)),
			)
			return
		}
		column.addHeaderCard(
			MascotForm.DEFAULT,
			getString(R.string.forecast_title),
			resources.getQuantityString(R.plurals.forecast_summary, entries.size, entries.size),
			mascotSizeDp = 56,
		)
		for (bucket in ReleaseForecast.Bucket.entries) {
			val inBucket = entries.filter { it.bucket == bucket }
			if (inBucket.isEmpty()) continue
			val body = column.addSectionCard(getString(bucket.titleRes()))
			inBucket.forEach { body.addRow(it) }
		}
		column.addBodyText(getString(R.string.forecast_disclaimer)).apply {
			setPadding(0, dp(4), 0, dp(8))
		}
	}

	private fun LinearLayout.addRow(entry: ReleaseForecast.Entry) {
		val row = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			minimumHeight = dp(48)
			setPadding(0, dp(8), 0, dp(8))
			isClickable = true
			isFocusable = true
			background = context.getThemeDrawable(android.R.attr.selectableItemBackground)
			setOnClickListener { AppRouter(this@ReleaseForecastActivity).openDetails(entry.mangaId) }
		}
		row.addView(context.bodyText(entry.title), rowParams())
		val date = DateUtils.formatDateTime(
			context,
			entry.expectedAt,
			DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_ALL,
		)
		row.addView(
			context.statusText(
				resources.getQuantityString(R.plurals.forecast_row_detail, entry.everyDays, entry.everyDays, date),
			),
			rowParams(),
		)
		addView(row, rowParams())
	}

	private fun rowParams() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

	private fun ReleaseForecast.Bucket.titleRes(): Int = when (this) {
		ReleaseForecast.Bucket.OVERDUE -> R.string.forecast_bucket_overdue
		ReleaseForecast.Bucket.TODAY -> R.string.forecast_bucket_today
		ReleaseForecast.Bucket.THIS_WEEK -> R.string.forecast_bucket_this_week
		ReleaseForecast.Bucket.LATER -> R.string.forecast_bucket_later
	}
}
