package org.koitharu.kotatsu.discover.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.discover.AniListDiscover
import org.koitharu.kotatsu.parsers.model.ContentRating
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * Loads every shelf of a media Discover page in parallel. Lives across configuration changes, so rotating the
 * screen keeps what was already loaded; nothing is cached on disk.
 */
@HiltViewModel
class MediaDiscoverViewModel @Inject constructor(
	@ApplicationContext private val context: Context,
	private val discover: AniListDiscover,
	private val repositories: MangaRepository.Factory,
	private val settings: AppSettings,
) : ViewModel() {

	private val _shelves = MutableStateFlow<List<MediaDiscoverShelfModel>>(emptyList())
	val shelves: StateFlow<List<MediaDiscoverShelfModel>> = _shelves.asStateFlow()

	private var mediaType: MediaType? = null
	private val jobs = ArrayList<Job>()

	/** Starts loading the shelves for [type]; does nothing when they are already loaded or loading. */
	fun start(type: MediaType) {
		if (mediaType == type) return
		mediaType = type
		reload()
	}

	/** Loads every shelf again, e.g. after all of them failed. */
	fun reload() {
		val type = mediaType ?: return
		jobs.forEach { it.cancel() }
		jobs.clear()
		val specs = MediaDiscoverShelves.forType(type)
		_shelves.value = specs.map { MediaDiscoverShelfModel(it, MediaDiscoverShelfState.Loading) }
		specs.forEach { shelf ->
			jobs += viewModelScope.launch {
				// withTimeoutOrNull: a timeout is a CancellationException, which runCatchingCancellable rethrows and
				// would leave the shelf spinning forever instead of hiding it.
				val items = runCatchingCancellable {
					withTimeoutOrNull(LOAD_TIMEOUT_MS) { load(shelf) }
				}.getOrNull().orEmpty()
				val visible = if (settings.isNsfwContentDisabled) items.filterNot { it.isAdult } else items
				val state = if (visible.isEmpty()) {
					MediaDiscoverShelfState.Hidden
				} else {
					MediaDiscoverShelfState.Loaded(visible.distinctBy { it.id }.take(MAX_ITEMS))
				}
				_shelves.update { list ->
					list.map { if (it.shelf.key == shelf.key) it.copy(state = state) else it }
				}
			}
		}
	}

	private suspend fun load(shelf: MediaDiscoverShelf): List<AniListDiscover.Item> {
		shelf.source?.let { source ->
			return repositories.create(source).getList(0, null, null).map { manga ->
				AniListDiscover.Item(
					id = manga.id.hashCode(),
					title = manga.title,
					alternativeTitle = null,
					coverUrl = manga.coverUrl?.takeIf { it.startsWith("https://") },
					score = null,
					isAdult = manga.contentRating == ContentRating.ADULT,
					manga = manga,
				)
			}
		}
		shelf.feed?.let { feed ->
			return discover.load(feed, 1).items
		}
		if (shelf.airing) {
			return discover.loadAiring(1, 7, ::airingLabel).items
		}
		return emptyList()
	}

	/** "Ep 5 · Tue 18:30" in the device's locale and time zone. */
	private fun airingLabel(episode: Int, airingAt: Long): String {
		val time = SimpleDateFormat("EEE HH:mm", Locale.getDefault()).format(Date(airingAt * 1000))
		return if (episode > 0) context.getString(R.string.media_discover_airing_episode, episode, time) else time
	}

	private companion object {

		const val LOAD_TIMEOUT_MS = 25_000L
		const val MAX_ITEMS = 30
	}
}
