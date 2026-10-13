package org.koitharu.kotatsu.stats.ui

import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.take
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.FavouriteCategory
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.stats.domain.StatsInsights
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.ui.util.ReversibleAction
import org.koitharu.kotatsu.core.util.ext.MutableEventFlow
import org.koitharu.kotatsu.core.util.ext.call
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.stats.data.StatsRepository
import org.koitharu.kotatsu.stats.domain.StatsPeriod
import org.koitharu.kotatsu.stats.domain.StatsRecord
import javax.inject.Inject

@HiltViewModel
class StatsViewModel @Inject constructor(
	private val repository: StatsRepository,
	private val settings: AppSettings,
	favouritesRepository: FavouritesRepository,
) : BaseViewModel() {

	val period = MutableStateFlow(StatsPeriod.WEEK)
	val onActionDone = MutableEventFlow<ReversibleAction>()
	val selectedCategories = MutableStateFlow<Set<Long>>(emptySet())
	val favoriteCategories = favouritesRepository.observeCategories()
		.take(1)

	val readingStats = MutableStateFlow<List<StatsRecord>>(emptyList())
	val dailyTotals = MutableEventFlow<String>()
	internal val insights = MutableEventFlow<StatsScreenInsights>()

	init {
		launchJob(Dispatchers.Default) {
			combine<StatsPeriod, Set<Long>, Pair<StatsPeriod, Set<Long>>>(
				period,
				selectedCategories,
				::Pair,
			).collectLatest { p ->
				readingStats.value = withLoading {
					repository.getReadingStats(p.first, p.second)
				}
			}
		}
	}

	fun setCategoryChecked(category: FavouriteCategory, checked: Boolean) {
		val snapshot = selectedCategories.value.toMutableSet()
		if (checked) {
			snapshot.add(category.id)
		} else {
			snapshot.remove(category.id)
		}
		selectedCategories.value = snapshot
	}

	fun requestDailyTotals() {
		launchLoadingJob(Dispatchers.Default) {
			val byDay = repository.getDailyReadingTime(30)
			val text = if (byDay.isEmpty()) "No reading activity in the last 30 days" else
				byDay.entries.joinToString("\n") { (day, milliseconds) ->
					"%04d-%02d-%02d: %d minutes".format(day.year, day.month, day.day, milliseconds / 60_000L)
				}
			dailyTotals.call(text)
		}
	}

	fun currentGoalMinutes(): Int = settings.dailyReadingGoalMinutes

	fun setDailyGoal(minutes: Int) {
		settings.dailyReadingGoalMinutes = minutes
		requestInsights()
	}

	fun requestInsights() {
		launchLoadingJob(Dispatchers.Default) {
			val goal = settings.dailyReadingGoalMinutes
			val summary = repository.getInsights(goal)
			val media = repository.getMediaTotals(StatsPeriod.MONTH)
			insights.call(StatsScreenInsights(goal, summary, media))
		}
	}

	fun clearStats() {
		launchLoadingJob(Dispatchers.Default) {
			repository.clearStats()
			readingStats.value = emptyList()
			onActionDone.call(ReversibleAction(R.string.stats_cleared, null))
		}
	}
}

internal data class StatsScreenInsights(
    val goalMinutes: Int,
    val summary: StatsInsights.Summary,
    val media: Map<MediaType, Long>,
)
