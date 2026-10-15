package org.koitharu.kotatsu.search.ui.multi

import org.koitharu.kotatsu.favourites.domain.PrivateCategories
import android.content.Context
import androidx.collection.ArraySet
import androidx.collection.LongSet
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.PublicBookCatalog
import org.koitharu.kotatsu.core.media.OpdsCatalog
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.media.filterByMediaType
import org.koitharu.kotatsu.core.model.LocalMangaSource
import org.koitharu.kotatsu.core.model.UnknownMangaSource
import org.koitharu.kotatsu.core.model.getLocale
import org.koitharu.kotatsu.core.ui.model.MangaOverride
import org.koitharu.kotatsu.list.ui.model.MangaCompactListModel
import org.koitharu.kotatsu.list.ui.model.MangaDetailedListModel
import org.koitharu.kotatsu.list.ui.model.MangaGridModel
import org.koitharu.kotatsu.list.ui.model.MangaListModel
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.parser.MangaRepository
import org.koitharu.kotatsu.core.parser.ParserMangaRepository
import org.koitharu.kotatsu.core.prefs.ListMode
import org.koitharu.kotatsu.core.ui.BaseViewModel
import org.koitharu.kotatsu.core.util.ext.append
import org.koitharu.kotatsu.core.util.ext.printStackTraceDebug
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.explore.data.MangaSourcesRepository
import org.koitharu.kotatsu.explore.data.SourcePresetsRepository
import org.koitharu.kotatsu.favourites.domain.FavouritesRepository
import org.koitharu.kotatsu.history.data.HistoryRepository
import org.koitharu.kotatsu.list.domain.MangaListMapper
import org.koitharu.kotatsu.list.ui.model.ButtonFooter
import org.koitharu.kotatsu.list.ui.model.EmptyState
import org.koitharu.kotatsu.list.ui.model.ListModel
import org.koitharu.kotatsu.list.ui.model.LoadingFooter
import org.koitharu.kotatsu.list.ui.model.LoadingState
import org.koitharu.kotatsu.parsers.model.Manga
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.MangaSource
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.domain.SearchV2Helper
import org.koitharu.kotatsu.sourcescore.domain.SourceRanker
import org.koitharu.kotatsu.sourcehealth.SourceHealthRecorder
import org.koitharu.kotatsu.sourcehealth.SourceWebsiteGroups
import java.util.Locale
import javax.inject.Inject

private const val MAX_PARALLELISM = 8

/** Highest value [SearchViewModel.priority] returns: +2 for a locale match. */
private const val MAX_LOCALE_PRIORITY = 2.0

@HiltViewModel
class SearchViewModel @Inject constructor(
	private val savedStateHandle: SavedStateHandle,
	@ApplicationContext private val applicationContext: Context,
	private val publicBookCatalog: PublicBookCatalog,
	private val opdsCatalog: OpdsCatalog,
	private val mangaListMapper: MangaListMapper,
	private val searchHelperFactory: SearchV2Helper.Factory,
	private val sourcesRepository: MangaSourcesRepository,
	private val historyRepository: HistoryRepository,
	private val favouritesRepository: FavouritesRepository,
	private val privateCategories: PrivateCategories,
	private val settings: AppSettings,
	private val presetsRepository: SourcePresetsRepository,
	private val sourceRanker: SourceRanker,
    private val sourceHealth: SourceHealthRecorder,
    private val repositoryFactory: MangaRepository.Factory,
) : BaseViewModel() {

	val query = savedStateHandle.get<String>(AppRouter.KEY_QUERY).orEmpty()
	val kind = savedStateHandle.get<SearchKind>(AppRouter.KEY_KIND) ?: SearchKind.SIMPLE
	val searchMediaScope: String? = savedStateHandle.get(SearchActivity.EXTRA_MEDIA_TYPE)
	val mediaTypeOverride: MediaType? = searchMediaScope
		?.let { raw -> MediaType.entries.firstOrNull { it.name == raw } }
	val selectedMediaScope: String
		get() = searchMediaScope ?: settings.mediaTypeFilter?.name ?: "ALL"

	private val effectiveMediaType: MediaType?
		get() = if (searchMediaScope == "ALL") null else mediaTypeOverride ?: settings.mediaTypeFilter

	private var includeDisabledSources = MutableStateFlow(false)
	private var pinnedOnly = MutableStateFlow(false)
	// The combined search is most useful as a single clean answer, so dead/empty sources stay hidden
	// unless the user explicitly asks to see them.
	private var hideEmpty = MutableStateFlow(true)
	private var yearFrom = MutableStateFlow<Int?>(null)
	private var yearTo = MutableStateFlow<Int?>(null)
	/** When off, titles whose year can't be estimated are hidden while a year filter or order is in use. */
	private val languageFilter = MutableStateFlow<String?>(savedStateHandle.get<String>("language_filter"))
	private val includeUnknownYears = MutableStateFlow(savedStateHandle.get<Boolean>("include_unknown_years") ?: true)
    val isVideoModeSearch: Boolean
        get() = effectiveMediaType == MediaType.VIDEO

    var videoResultMode: VideoResultMode = savedStateHandle.get<String>("video_result_mode")
        ?.let { raw -> VideoResultMode.entries.firstOrNull { it.name == raw } }
        ?: VideoResultMode.VIDEOS
        private set

    fun setVideoResultMode(mode: VideoResultMode) {
        if (videoResultMode == mode) return
        videoResultMode = mode
        savedStateHandle["video_result_mode"] = mode.name
        retry()
    }

	private val results = MutableStateFlow<List<SearchResultsListModel>>(emptyList())
	private val publicationSort = MutableStateFlow(
		savedStateHandle.get<String>("publication_sort")
			?.let { code -> PublicationYearOrder.entries.firstOrNull { it.name == code } }
			?: PublicationYearOrder.RELEVANCE,
	)
	private val displayResults = combine(
		combine(results, languageFilter) { all, language ->
			if (language == null) all else all.filter { LanguageFilter.matches(it.source.getLocale()?.language, language) }
		},
		publicationSort, yearFrom, yearTo, includeUnknownYears,
	) { raw, order, from, to, unknown ->
		val sections = if (YearFilter.isActive(from, to, unknown)) {
			raw.map { section ->
				section.copy(list = section.list.filter {
					YearFilter.matches(
						PublicationYearSorter.estimateYear(it.manga.title, it.manga.altTitle, it.manga.description),
						from, to, unknown,
					)
				})
			}
		} else raw
		val ordered = if (order == PublicationYearOrder.RELEVANCE) sections
		else if (order.isGlobal) mergeGlobally(sections.sortedForDisplay(), order)
		else sections.map { section ->
			section.copy(list = PublicationYearSorter.sorted(section.list, order))
		}
		// While years matter (an order or a range is chosen) show each year in the title, "~" when it is only a guess.
		if (order == PublicationYearOrder.RELEVANCE && from == null && to == null) ordered
		else ordered.map { section -> section.copy(list = section.list.map { it.withYearBadge() }) }
	}
	private fun MangaListModel.withYearBadge(): MangaListModel {
		val estimate = PublicationYearSorter.estimateDetailed(manga.title, manga.altTitle, manga.description) ?: return this
		val decorated = (override ?: MangaOverride(null, null, null)).copy(title = "$title (${estimate.badge})")
		return when (this) {
			is MangaCompactListModel -> copy(override = decorated)
			is MangaGridModel -> copy(override = decorated)
			is MangaDetailedListModel -> copy(override = decorated)
		}
	}

	/** Languages of the sources that answered so far, for the language filter. */
	fun languagesInResults(): List<String> =
		LanguageFilter.available(results.value.map { it.source.getLocale()?.language })

	val selectedLanguage: String?
		get() = languageFilter.value

	fun setLanguageFilter(language: String?) {
		languageFilter.value = language
		savedStateHandle["language_filter"] = language
	}

	/** One combined, globally ordered section replaces the per-source ones (sections with errors stay visible). */
	private fun mergeGlobally(
		sections: List<SearchResultsListModel>,
		order: PublicationYearOrder,
	): List<SearchResultsListModel> {
		val withResults = sections.filter { it.list.isNotEmpty() }
		if (withResults.isEmpty()) return sections
		val merged = PublicationYearSorter.sortedGlobal(
			lists = withResults.map { it.list },
			order = order,
			titleOf = { it.manga.title },
			yearOf = { PublicationYearSorter.estimateYear(it.manga.title, it.manga.altTitle, it.manga.description) },
		)
		val combined = SearchResultsListModel(
			titleResId = R.string.search_all_sources_by_year,
			source = withResults.first().source,
			listFilter = null,
			sortOrder = null,
			list = merged,
			error = null,
		)
		return listOf(combined) + sections.filter { it.list.isEmpty() && it.error != null }
	}

	/** Public catalog links use real URLs rather than invented Manga parser entries. */
	private data class CatalogState(
		val items: List<BookCatalogListModel> = emptyList(),
		val loading: Boolean = false,
	)
	private val catalogState = MutableStateFlow(CatalogState(loading = mediaTypeOverride == MediaType.BOOK))
	private var catalogJob: Job? = null

	/**
	 * Where each source's results belong on screen, keyed by source name. Sources answer in whatever
	 * order the network allows; without this a fast obscure source would push a slow popular one
	 * down the page. Filled in before a sweep starts, so every result finds its entry.
	 */
	private val displayRanks = java.util.concurrent.ConcurrentHashMap<String, DisplayRank>()

	private var searchJob: Job? = null

	val list: StateFlow<List<ListModel>> = combine(
		displayResults,
		isLoading.dropWhile { !it },
		includeDisabledSources,
		hideEmpty,
		catalogState,
	) { list, loading, includeDisabled, hideEmptyVal, catalog ->
		val filteredList = if (hideEmptyVal) {
			list.filter { it.list.isNotEmpty() }
		} else {
			list
		}.sortedForDisplay()
		val combined: List<ListModel> = filteredList + catalog.items
		when {
			combined.isEmpty() -> listOf(
				when {
					loading || catalog.loading -> LoadingState()
					else -> EmptyState(
						icon = R.drawable.kitsune_sticker_search,
						textPrimary = R.string.nothing_found,
						textSecondary = R.string.text_search_holder_secondary,
						actionStringRes = 0,
					)
				},
			)

			loading || catalog.loading -> combined + LoadingFooter()
			includeDisabled -> combined
			else -> combined + ButtonFooter(R.string.search_disabled_sources)
		}
	}.stateIn(viewModelScope + Dispatchers.Default, SharingStarted.Eagerly, listOf(LoadingState()))

	init {
		doSearch()
	}

	fun getItems(ids: LongSet): Set<Manga> {
		val snapshot = results.value
		val result = ArraySet<Manga>(ids.size)
		snapshot.forEach { x ->
			for (item in x.list) {
				if (item.id in ids) {
					result.add(item.manga)
				}
			}
		}
		return result
	}

	fun retry() {
		searchJob?.cancel()
		catalogJob?.cancel()
		catalogState.value = CatalogState(loading = mediaTypeOverride == MediaType.BOOK && !hasPublicationYearFilter)

		includeDisabledSources.value = false
		doSearch()
	}

	fun setPinnedOnly(value: Boolean) {
		if (pinnedOnly.value != value) {
			pinnedOnly.value = value
			retry()
		}
	}

	fun setHideEmpty(value: Boolean) {
		hideEmpty.value = value
	}

	val isHideEmptyEnabled: Boolean
		get() = hideEmpty.value

	val isPinnedOnly: Boolean
		get() = pinnedOnly.value

	val publicationYearOrder: PublicationYearOrder
		get() = publicationSort.value

	fun setPublicationYearOrder(order: PublicationYearOrder) {
		publicationSort.value = order
		savedStateHandle["publication_sort"] = order.name
	}

	val publicationYearFrom: Int?
		get() = yearFrom.value

	val publicationYearTo: Int?
		get() = yearTo.value

	val includesUnknownYears: Boolean
		get() = includeUnknownYears.value

	fun setIncludeUnknownYears(value: Boolean) {
		includeUnknownYears.value = value
		savedStateHandle["include_unknown_years"] = value
	}

	val hasPublicationYearFilter: Boolean
		get() = yearFrom.value != null || yearTo.value != null

	fun setPublicationYearRange(from: Int?, to: Int?) {
		val normalizedFrom = from?.takeIf { it > 0 }
		val normalizedTo = to?.takeIf { it > 0 }
		if (yearFrom.value == normalizedFrom && yearTo.value == normalizedTo) return
		yearFrom.value = normalizedFrom
		yearTo.value = normalizedTo
		retry()
	}

	fun continueSearch() {
		if (includeDisabledSources.value) {
			return
		}
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.Default) {
			includeDisabledSources.value = true
			prevJob?.join()
			val sources = if (pinnedOnly.value) {
				emptyList()
			} else {
				sourcesRepository.getDisabledSources().toList()
					.filterByMediaType(effectiveMediaType)
					.rankedForSweep(batch = 1)
			}
            val excluded = runCatchingCancellable { sourceHealth.unhealthyNames() }.getOrDefault(emptySet())
			val semaphore = Semaphore(MAX_PARALLELISM)
            SourceWebsiteGroups.groups(sources.filterNot { it.name in excluded }, ::parserDomain).map { group ->
                launch {
                    semaphore.withPermit {
                        appendResult(searchHostGroup(group))
                    }
                }
            }.joinAll()
		}
	}

	private fun doSearch() {
		if (mediaTypeOverride == MediaType.BOOK && !hasPublicationYearFilter) {
			catalogJob = viewModelScope.launch(Dispatchers.IO) {
				val publicResults = supervisorScope {
					listOf(
						async { safeCatalogSearch { publicBookCatalog.searchInternetArchive(query) } },
						async { safeCatalogSearch { publicBookCatalog.searchStandardEbooks(query) } },
						async { searchSavedOpds(query) },
					).awaitAll().flatten()
				}
				catalogState.value = CatalogState(publicResults.distinctBy { it.url })
			}
		} else {
			catalogState.value = CatalogState()
		}
		val prevJob = searchJob
		searchJob = launchLoadingJob(Dispatchers.Default) {
			prevJob?.cancelAndJoin()
			results.value = emptyList()
			displayRanks.clear()
			// History/favourites/local rows do not carry a trustworthy publication year, so omit them
			// while a year filter is active instead of pretending they matched it.
			// A book-only sweep must not accidentally show manga favourites/history/local manga.
			// We will include those collections once they carry reliable cross-media metadata.
			if (!hasPublicationYearFilter && mediaTypeOverride == null) {
				appendResult(searchHistory())
				appendResult(searchFavorites())
				appendResult(searchLocal())
			}
			val sources = getPresetSourcesOrDefault().rankedForSweep(batch = 0)
            val excluded = runCatchingCancellable { sourceHealth.unhealthyNames() }.getOrDefault(emptySet())
			val semaphore = Semaphore(MAX_PARALLELISM)
            SourceWebsiteGroups.groups(sources.filterNot { it.name in excluded }, ::parserDomain).map { group ->
                launch {
                    semaphore.withPermit {
                        appendResult(searchHostGroup(group))
                    }
                }
            }.joinAll()
		}
	}

    private fun parserDomain(source: MangaParserSource): String? = runCatching {
        (repositoryFactory.create(source) as? ParserMangaRepository)?.domain
    }.getOrNull()

    /** One result section per known host; retry alternatives if the preferred site is empty or fails. */
    private suspend fun searchHostGroup(group: List<MangaSource>): SearchResultsListModel? {
        var lastError: SearchResultsListModel? = null
        for (source in group) {
            val result = searchSource(source)
            if (result != null && result.list.isNotEmpty()) return result
            if (result?.error != null) lastError = result
        }
        return lastError
    }

	/** Public catalogs share the Books search list without masquerading as manga parsers. */
	private suspend fun safeCatalogSearch(
		block: suspend () -> List<PublicBookCatalog.Book>,
	): List<BookCatalogListModel> = try {
		withTimeout(15_000) {
			block().map { BookCatalogListModel(it.title, it.author, it.catalog, it.url) }
		}
	} catch (_: TimeoutCancellationException) {
		emptyList()
	} catch (cancel: CancellationException) {
		throw cancel
	} catch (_: Exception) {
		emptyList()
	}

	/** Saved OPDS feeds can be searched within their first page; full remote OPDS search is not assumed. */
	private suspend fun searchSavedOpds(term: String): List<BookCatalogListModel> {
		val saved = applicationContext.getSharedPreferences("opds_catalogs", Context.MODE_PRIVATE)
			.getStringSet("urls", emptySet()).orEmpty().take(3)
		return supervisorScope {
			saved.map { url ->
				async {
					try {
						withTimeout(15_000) {
							opdsCatalog.load(url).asSequence()
								.filter { it.title.contains(term, ignoreCase = true) ||
									it.author.contains(term, ignoreCase = true) }
								.take(35)
								.map { BookCatalogListModel(it.title, it.author, "Saved OPDS", it.href) }
								.toList()
						}
					} catch (_: TimeoutCancellationException) {
						emptyList()
					} catch (cancel: CancellationException) {
						throw cancel
					} catch (_: Exception) {
						emptyList()
					}
				}
			}.awaitAll().flatten()
		}
	}

	// impl

	private suspend fun searchSource(source: MangaSource): SearchResultsListModel? = runCatchingCancellable {
		val searchHelper = searchHelperFactory.create(source)
		searchHelper(if (isVideoModeSearch) videoResultMode.queryFor(source, query) else query, kind, yearFrom.value, yearTo.value)
	}.fold(
		onSuccess = { result ->
			if (result == null || result.manga.isEmpty()) {
				null
			} else {
				val list = mangaListMapper.toListModelList(
					manga = result.manga,
					mode = ListMode.GRID,
				)
				SearchResultsListModel(
					titleResId = 0,
					source = source,
					list = list,
					error = null,
					listFilter = result.listFilter,
					sortOrder = result.sortOrder,
					isHot = displayRanks[source.name]?.isHot == true,
				)
			}
		},
		onFailure = { error ->
			error.printStackTraceDebug()
			if (source is MangaParserSource && source.isBroken) {
				null
			} else {
				SearchResultsListModel(
					titleResId = 0,
					source = source,
					listFilter = null,
					sortOrder = null,
					list = emptyList(),
					error = error,
					isHot = displayRanks[source.name]?.isHot == true,
				)
			}
		},
	)

	private suspend fun searchHistory(): SearchResultsListModel? = runCatchingCancellable {
		historyRepository.search(query, kind, Int.MAX_VALUE)
	}.fold(
		onSuccess = { result ->
			if (result.isNotEmpty()) {
				SearchResultsListModel(
					titleResId = R.string.history,
					source = UnknownMangaSource,
					list = mangaListMapper.toListModelList(manga = result, mode = ListMode.GRID),
					error = null,
					listFilter = null,
					sortOrder = null,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = R.string.history,
				source = UnknownMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private suspend fun searchFavorites(): SearchResultsListModel? = runCatchingCancellable {
		favouritesRepository.search(query, kind, Int.MAX_VALUE).filterNot { manga ->
			// Titles in a locked private category don't show up in search either.
			favouritesRepository.getCategoriesIds(manga.id).any { privateCategories.isHidden(it) }
		}
	}.fold(
		onSuccess = { result ->
			if (result.isNotEmpty()) {
				SearchResultsListModel(
					titleResId = R.string.favourites,
					source = UnknownMangaSource,
					list = mangaListMapper.toListModelList(
						manga = result,
						mode = ListMode.GRID,
						flags = MangaListMapper.NO_FAVORITE,
					),
					error = null,
					listFilter = null,
					sortOrder = null,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = R.string.favourites,
				source = UnknownMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private suspend fun searchLocal(): SearchResultsListModel? = runCatchingCancellable {
		searchHelperFactory.create(LocalMangaSource).invoke(query, kind)
	}.fold(
		onSuccess = { result ->
			if (!result?.manga.isNullOrEmpty()) {
				SearchResultsListModel(
					titleResId = 0,
					source = LocalMangaSource,
					list = mangaListMapper.toListModelList(
						manga = result.manga,
						mode = ListMode.GRID,
						flags = MangaListMapper.NO_SAVED,
					),
					error = null,
					listFilter = result.listFilter,
					sortOrder = result.sortOrder,
				)
			} else {
				null
			}
		},
		onFailure = { error ->
			SearchResultsListModel(
				titleResId = 0,
				source = LocalMangaSource,
				list = emptyList(),
				error = error,
				listFilter = null,
				sortOrder = null,
			)
		},
	)

	private fun appendResult(item: SearchResultsListModel?) {
		if (item != null) {
			results.append(item)
		}
	}

	private fun MangaSource.priority(): Int {
		val sourceLocale = getLocale() ?: return 0
		return if (sourceLocale.language == Locale.getDefault().language) 2 else 0
	}

	/**
	 * Orders a sweep so the sources most likely to answer are asked first.
	 *
	 * The semaphore below already serialises work in list order, so ordering the list *is* the
	 * scheduling - no queue rewrite needed. Results therefore surface best-source-first instead of
	 * in whatever order the source table happened to return.
	 *
	 * [SourceRanker.RankingSnapshot.order] also reserves a slot for an under-sampled source, which is
	 * what stops a newly added source from never being queried and therefore never earning a score.
	 *
	 * Falls back to the previous locale heuristic if the community database cannot be read.
	 */
	private suspend fun List<MangaSource>.rankedForSweep(batch: Int): List<MangaSource> {
		if (isEmpty()) return this
		val snapshot = runCatchingCancellable { sourceRanker.snapshot() }.getOrNull()
			?: return sortedByDescending { it.priority() }
		val ranked = snapshot.order(
			sources = this,
			affinityOf = { source -> source.priority() / MAX_LOCALE_PRIORITY },
		)
		ranked.forEach { displayRanks[it.source.name] = DisplayRank(batch, it.isHot, it.composite) }
		return ranked.map { it.source }
	}

	/**
	 * History, favourites and local results keep their place at the top. Source results follow by
	 * rank: popular sources first, then best score. Deliberately not the sweep order, which puts a
	 * random under-sampled source near the front to let it earn a score - right for querying, wrong
	 * for what the user reads first.
	 *
	 * The sort is stable, so anything without a rank (the ranker was unavailable) keeps arrival order.
	 */
	private fun List<SearchResultsListModel>.sortedForDisplay(): List<SearchResultsListModel> = sortedWith(
		compareBy<SearchResultsListModel> { displayRanks[it.source.name]?.batch ?: -1 }
			.thenByDescending { displayRanks[it.source.name]?.isHot == true }
			.thenByDescending { displayRanks[it.source.name]?.composite ?: 0.0 },
	)

	private data class DisplayRank(
		/** 0 for the user's own sources, 1 for the "search disabled sources" follow-up, which stays below. */
		val batch: Int,
		val isHot: Boolean,
		val composite: Double,
	)

	private suspend fun getPresetSourcesOrDefault(): List<MangaSource> {
		// An explicit Books/Anime search uses all enabled sources of that kind, even when
		// the user's current manga source preset contains no novel or video websites.
		val sources = when {
			pinnedOnly.value -> sourcesRepository.getPinnedSources().toList()
			mediaTypeOverride != null -> sourcesRepository.getEnabledSources()
			else -> getSourcesBeforeMediaFilter()
		}
        val excluded = runCatchingCancellable { sourceHealth.unhealthyNames() }.getOrDefault(emptySet())
		return sources.filterByMediaType(effectiveMediaType).filterNot { it.name in excluded }
	}

	private suspend fun getSourcesBeforeMediaFilter(): List<MangaSource> {
		val presetId = settings.activeSourcePresetId
		if (presetId != 0L) {
			val preset = presetsRepository.getById(presetId)
			if (preset != null) {
				return sourcesRepository.getSourcesByNames(preset.sources)
			}
		}
		return if (pinnedOnly.value) {
			sourcesRepository.getPinnedSources().toList()
		} else {
			sourcesRepository.getEnabledSources()
		}
	}
}
