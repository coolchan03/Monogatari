package org.koitharu.kotatsu.stats.ui

import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import org.koitharu.kotatsu.core.qa.CsvExportFormatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewStub
import android.widget.CompoundButton
import android.widget.ScrollView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.ProgressBar
import androidx.activity.viewModels
import androidx.appcompat.widget.PopupMenu
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePaddingRelative
import androidx.recyclerview.widget.AsyncListDiffer
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.BaseListAdapter
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.KotatsuColors
import org.koitharu.kotatsu.core.util.ext.end
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.setTextAndVisible
import org.koitharu.kotatsu.core.util.ext.showOrHide
import org.koitharu.kotatsu.core.util.ext.start
import org.koitharu.kotatsu.databinding.ActivityStatsBinding
import org.koitharu.kotatsu.databinding.ItemEmptyStateBinding
import org.koitharu.kotatsu.list.ui.adapter.ListItemType
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.koitharu.kotatsu.stats.domain.StatsRecord
import org.koitharu.kotatsu.stats.ui.views.PieChartView
import org.koitharu.kotatsu.stats.ui.views.ReadingHeatmapView

@AndroidEntryPoint
class StatsActivity : BaseActivity<ActivityStatsBinding>(),
	OnListItemClickListener<Manga>,
	PieChartView.OnSegmentClickListener,
	AsyncListDiffer.ListListener<StatsRecord>,
	ViewStub.OnInflateListener,
	View.OnClickListener,
	CompoundButton.OnCheckedChangeListener {

	private val viewModel: StatsViewModel by viewModels()
	private val csvFilePicker = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
		if (uri != null) {
			val records = viewModel.readingStats.value
			val csv = CsvExportFormatter.encode(listOf("Title", "DurationMs"), records.map {
				listOf(it.manga?.title ?: "Other", it.duration.toString())
			})
			runCatching { contentResolver.openOutputStream(uri)?.use { stream ->
				stream.write(csv.toByteArray(Charsets.UTF_8))
			} ?: error("Cannot open destination") }.onFailure { error ->
				android.widget.Toast.makeText(this, error.message ?: "CSV export failed", android.widget.Toast.LENGTH_LONG).show()
			}
		}
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityStatsBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		val adapter = BaseListAdapter<StatsRecord>()
			.addDelegate(ListItemType.FEED, statsAD(this))
			.addListListener(this)
		viewBinding.recyclerView.adapter = adapter
		viewBinding.chart.onSegmentClickListener = this
		viewBinding.stubEmpty.setOnInflateListener(this)
		viewBinding.chipPeriod.setOnClickListener(this)

		viewModel.isLoading.observe(this) {
			viewBinding.progressBar.showOrHide(it)
		}
		viewModel.period.observe(this) {
			viewBinding.chipPeriod.setText(it.titleResId)
		}
		viewModel.favoriteCategories.observe(this, ::createCategoriesChips)
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.recyclerView))
		viewModel.insights.observeEvent(this, ::showInsightsDialog)
		viewModel.dailyTotals.observeEvent(this) { summary ->
			androidx.appcompat.app.AlertDialog.Builder(this)
				.setTitle("Reading activity and insights")
				.setMessage(summary)
				.setPositiveButton(android.R.string.ok, null)
				.show()
		}
		viewModel.readingStats.observe(this) {
			val sum = it.sumOf { it.duration }
			viewBinding.chart.setData(
				it.map { v ->
					PieChartView.Segment(
						value = (v.duration / 1000).toInt(),
						label = v.manga?.title ?: getString(R.string.other_manga),
						percent = if (sum > 0L) (v.duration.toDouble() / sum).toFloat() else 0f,
						color = KotatsuColors.ofManga(this, v.manga),
						tag = v.manga,
					)
				},
			)
			adapter.emit(it)
		}
	}

	override fun onApplyWindowInsets(
		v: View,
		insets: WindowInsetsCompat
	): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		val isTablet = viewBinding.guidelineCenter != null
		viewBinding.appbar.updatePaddingRelative(
			start = bars.start(v),
			top = bars.top,
			end = if (isTablet) 0 else bars.end(v),
		)
		val badgePadding = resources.getDimensionPixelOffset(R.dimen.list_spacing_large)
		viewBinding.scrollViewChips.updatePaddingRelative(
			start = badgePadding + if (isTablet) 0 else bars.start(v),
			end = badgePadding + bars.end(v),
			top = if (isTablet) bars.top else 0,
		)
		viewBinding.recyclerView.updatePaddingRelative(
			start = if (isTablet) 0 else bars.start(v),
			end = bars.end(v),
			bottom = bars.bottom,
		)
		viewBinding.chart.updateLayoutParams<ViewGroup.MarginLayoutParams> {
			val baseMargin = topMargin
			bottomMargin = if (isTablet) baseMargin + bars.bottom else baseMargin
			marginStart = baseMargin + bars.start(v)
			marginEnd = if (isTablet) baseMargin else baseMargin + bars.end(v)
		}
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
			.build()
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.chip_period -> showPeriodSelector()
		}
	}

	override fun onCheckedChanged(buttonView: CompoundButton, isChecked: Boolean) {
		val category = buttonView.tag as? FavouriteCategory ?: return
		viewModel.setCategoryChecked(category, isChecked)
	}

	override fun onItemClick(item: Manga, view: View) {
		router.showStatisticSheet(item)
	}

	override fun onSegmentClick(view: PieChartView, segment: PieChartView.Segment) {
		val manga = segment.tag as? Manga ?: return
		onItemClick(manga, view)
	}

	override fun onCreateOptionsMenu(menu: Menu?): Boolean {
		menuInflater.inflate(R.menu.opt_stats, menu)
		menu?.add(0, 7201, 0, "Export reading statistics (CSV)")
		menu?.add(0, 7202, 1, "Daily reading totals")
		menu?.add(0, 7204, 2, "Reading insights and streaks")
		menu?.add(0, 7205, 3, "Daily reading goal")
		return super.onCreateOptionsMenu(menu)
	}

	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		return when (item.itemId) {
			7201 -> {
				csvFilePicker.launch("monogatari-reading-stats.csv")
				true
			}
			7202 -> {
				viewModel.requestDailyTotals()
				true
			}
			7204 -> {
				viewModel.requestInsights()
				true
			}
			7205 -> {
				showDailyGoalSelector()
				true
			}
			R.id.action_clear -> {
				showClearConfirmDialog()
				true
			}

			else -> super.onOptionsItemSelected(item)
		}
	}

	override fun onCurrentListChanged(previousList: MutableList<StatsRecord>, currentList: MutableList<StatsRecord>) {
		val isEmpty = currentList.isEmpty()
		with(viewBinding) {
			chart.isGone = isEmpty
			recyclerView.isGone = isEmpty
			stubEmpty.isVisible = isEmpty
		}
	}

	override fun onInflate(stub: ViewStub?, inflated: View) {
		val stubBinding = ItemEmptyStateBinding.bind(inflated)
		stubBinding.icon.setImageAsync(R.drawable.ic_empty_history)
		stubBinding.textPrimary.setText(R.string.text_empty_holder_primary)
		stubBinding.textSecondary.setTextAndVisible(R.string.empty_stats_text)
		stubBinding.buttonRetry.isVisible = false
	}

	private fun createCategoriesChips(categories: List<FavouriteCategory>) {
		val container = viewBinding.layoutChips
		if (container.childCount > 1) {
			// avoid duplication
			return
		}
		val checkedIds = viewModel.selectedCategories.value
		for (category in categories) {
			val chip = Chip(this)
			val drawable = ChipDrawable.createFromAttributes(this, null, 0, R.style.Widget_Kotatsu_Chip_Filter)
			chip.setChipDrawable(drawable)
			chip.text = category.title
			chip.tag = category
			chip.isChecked = category.id in checkedIds
			chip.setOnCheckedChangeListener(this)
			container.addView(chip)
		}
	}

    private fun showInsightsDialog(report: StatsScreenInsights) {
        val detail = report.summary
        val dp = resources.displayMetrics.density
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((18 * dp).toInt(), (8 * dp).toInt(), (18 * dp).toInt(), (16 * dp).toInt())
        }
        fun addText(value: String, size: Float = 15f) {
            body.addView(TextView(this).apply {
                text = value
                textSize = size
                setPadding(0, (7 * dp).toInt(), 0, (5 * dp).toInt())
            })
        }
        fun minutes(timeMs: Long): String = "${timeMs.coerceAtLeast(0L) / 60_000L} min"
        addText("Daily goal: ${minutes(detail.todayMs)} / ${report.goalMinutes} min", 18f)
        body.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = report.goalMinutes
            progress = (detail.todayMs / 60_000L).coerceIn(0L, max.toLong()).toInt()
        }, LinearLayout.LayoutParams(-1, (10 * dp).toInt()))
        addText("Current streak: ${detail.streakDays} days  ·  Goal reached: ${detail.goalDaysIn30}/30 days")
        addText("Last 7 days: ${minutes(detail.weekMs)}  ·  This month: ${minutes(detail.monthMs)}")
        val backlog = report.backlog
        addText("Cached unread backlog (estimate)", 18f)
        if (backlog.unreadTitlesEstimated == 0) {
            addText("No locally cached unread chapters with estimable lengths")
        } else {
            val hours = backlog.estimatedMs / 3_600_000L
            val mins = backlog.estimatedMs / 60_000L % 60
            addText("~$hours h $mins min across ${backlog.unreadTitlesEstimated} titles", 17f)
            if (backlog.chapterSizesGuessed > 0) {
                addText("${backlog.chapterSizesGuessed} chapter lengths estimated from title averages or defaults", 13f)
            }
        }
        if (backlog.titlesWithoutDuration > 0) {
            addText("${backlog.titlesWithoutDuration} cached video/audio titles lack playable duration metadata", 13f)
        }
        addText("Only locally cached chapter lists are included; this is not the complete online library backlog." +
            if (backlog.partialCache) " Cache scan reached its safety limit." else "", 13f)
        addText("Reading activity — last 30 days", 18f)
        body.addView(ReadingHeatmapView(this).apply {
            setData(detail.dailyMs, 7)
            contentDescription = "30 daily reading totals. Darker tiles indicate more activity."
        }, LinearLayout.LayoutParams(-1, -2))
        addText("29 days ago → today")
        addText("Reading by time of day — last 400 days", 18f)
        body.addView(ReadingHeatmapView(this).apply {
            setData(detail.hourMs, 12)
            contentDescription = "Activity from midnight through 11 PM. Darker tiles indicate more reading."
        }, LinearLayout.LayoutParams(-1, -2))
        addText("00:00–11:59 (top row)  ·  12:00–23:59 (bottom row)")
        addText("Time by media type", 18f)
        report.media.forEach { (period, totals) ->
            val label = when (period) {
                StatsPeriod.DAY -> "Today"
                StatsPeriod.WEEK -> "Last 7 days"
                StatsPeriod.MONTH -> "Last 30 days"
                else -> period.name.lowercase()
            }
            addText(label)
            if (totals.isEmpty()) addText("No recorded activity")
            totals.entries.sortedByDescending { it.value }.forEach { (kind, time) ->
                addText("  ${kind.name.lowercase().replaceFirstChar { it.uppercaseChar() }}: ${minutes(time)}")
            }
        }
        addText("Reading time by tag — last 30 days", 18f)
        if (report.tags.isEmpty()) {
            addText("No tagged reading activity yet")
        } else {
            report.tags.forEach { share ->
                addText("  ${share.title}: ${String.format(java.util.Locale.getDefault(),
                    "%.1f%%", share.percent * 100.0)}")
            }
            addText("Each tag includes all time spent on titles with that tag. Tags overlap; totals may exceed 100%.", 13f)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Reading insights")
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

	private fun showDailyGoalSelector() {
		val options = intArrayOf(5, 10, 15, 20, 30, 45, 60, 90, 120)
		val labels = options.map { "$it minutes" }.toTypedArray()
		val checked = options.indexOf(viewModel.currentGoalMinutes())
		androidx.appcompat.app.AlertDialog.Builder(this)
			.setTitle("Daily reading goal")
			.setSingleChoiceItems(labels, checked) { dialog, index ->
				viewModel.setDailyGoal(options[index])
				dialog.dismiss()
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private fun showClearConfirmDialog() {
		buildAlertDialog(this, isCentered = true) {
			setMessage(R.string.clear_stats_confirm)
			setTitle(R.string.clear_stats)
			setIcon(R.drawable.ic_delete_all)
			setNegativeButton(android.R.string.cancel, null)
			setPositiveButton(R.string.clear) { _, _ -> viewModel.clearStats() }
		}.show()
	}

	private fun showPeriodSelector() {
		val menu = PopupMenu(this, viewBinding.chipPeriod)
		val selected = viewModel.period.value
		for ((i, branch) in StatsPeriod.entries.withIndex()) {
			val item = menu.menu.add(R.id.group_period, Menu.NONE, i, branch.titleResId)
			item.isCheckable = true
			item.isChecked = selected.ordinal == i
		}
		menu.menu.setGroupCheckable(R.id.group_period, true, true)

		menu.setOnMenuItemClickListener {
			StatsPeriod.entries.getOrNull(it.order)?.also {
				viewModel.period.value = it
			} != null
		}
		menu.show()
	}
}
