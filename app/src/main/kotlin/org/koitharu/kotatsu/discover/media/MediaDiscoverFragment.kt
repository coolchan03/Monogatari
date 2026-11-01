package org.koitharu.kotatsu.discover.media

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.color.MaterialColors
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.BookLibraryActivity
import org.koitharu.kotatsu.core.media.MediaAccents
import org.koitharu.kotatsu.core.media.MediaFeedActivity
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.media.UnifiedLibraryActivity
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.Mascot
import org.koitharu.kotatsu.core.ui.util.RecyclerViewOwner
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.databinding.FragmentMediaDiscoverBinding
import org.koitharu.kotatsu.databinding.ItemDiscoverBinding
import org.koitharu.kotatsu.databinding.ItemMediaDiscoverHeaderBinding
import org.koitharu.kotatsu.databinding.ItemMediaDiscoverShelfBinding
import org.koitharu.kotatsu.discover.AniListDiscover
import org.koitharu.kotatsu.discover.DiscoverLibraryFinder
import org.koitharu.kotatsu.discover.addDiscoverItemToLibrary
import org.koitharu.kotatsu.main.ui.MainActivity
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.multi.SearchActivity
import org.koitharu.kotatsu.search.ui.suggestion.MainSearchScope
import javax.inject.Inject
import com.google.android.material.R as materialR

/**
 * A Discover page for one kind of media (books or videos): a header with a type-scoped search field and shortcuts,
 * then shelves of covers, each loaded on its own from a built-in source or AniList. Shown as a main navigation tab
 * through [DiscoverBooksFragment] / [DiscoverVideoFragment], or inside [MediaDiscoverActivity].
 */
abstract class MediaDiscoverFragment : Fragment(), RecyclerViewOwner {

	abstract val mediaType: MediaType

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var libraryFinder: DiscoverLibraryFinder

	private val viewModel: MediaDiscoverViewModel by viewModels()

	private var _binding: FragmentMediaDiscoverBinding? = null
	private val binding: FragmentMediaDiscoverBinding get() = checkNotNull(_binding)

	private var headerAdapter: HeaderAdapter? = null
	private var shelvesAdapter: ShelvesAdapter? = null
	private val carouselPool = RecyclerView.RecycledViewPool()

	override val recyclerView: RecyclerView?
		get() = _binding?.recyclerView

	override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
		FragmentMediaDiscoverBinding.inflate(inflater, container, false).also { _binding = it }.root

	override fun onResume() {
		super.onResume()
		// This page is about one media type, so the main search bar searches just that type.
		MainSearchScope.followSection(requireContext(), mediaType.name)
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		val header = HeaderAdapter()
		val shelves = ShelvesAdapter()
		headerAdapter = header
		shelvesAdapter = shelves
		binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
		binding.recyclerView.adapter = ConcatAdapter(header, shelves)
		viewModel.start(mediaType)
		viewModel.shelves.observe(viewLifecycleOwner) { models ->
			val visible = models.filter { it.state !is MediaDiscoverShelfState.Hidden }
			shelves.submitList(visible)
			header.showEmptyMessage(models.isNotEmpty() && visible.isEmpty())
		}
	}

	override fun onDestroyView() {
		binding.recyclerView.adapter = null
		headerAdapter = null
		shelvesAdapter = null
		_binding = null
		super.onDestroyView()
	}

	private fun bindActions(binding: ItemMediaDiscoverHeaderBinding) {
		binding.chipsActions.removeAllViews()
		fun action(@StringRes title: Int, run: () -> Unit) {
			binding.chipsActions.addView(Chip(requireContext()).apply {
				setText(title)
				isCheckable = false
				MediaAccents.tintChip(requireContext(), mediaType.name, this)
				setOnClickListener { run() }
			})
		}
		when (mediaType) {
			MediaType.VIDEO -> {
				action(R.string.media_discover_action_video_rss) {
					startActivity(Intent(requireContext(), MediaFeedActivity::class.java)
						.putExtra(MediaFeedActivity.EXTRA_MEDIA_TYPE, MediaType.VIDEO.name))
				}
				action(R.string.media_discover_action_saved_videos) { openLibrary(MediaType.VIDEO) }
				action(R.string.media_discover_action_browse_video_sources) { openExploreTab() }
			}

			MediaType.BOOK -> {
				action(R.string.media_discover_action_books_catalog) {
					startActivity(Intent(requireContext(), BookLibraryActivity::class.java))
				}
				action(R.string.media_discover_action_my_books) { openLibrary(MediaType.BOOK) }
				action(R.string.media_discover_action_search_novels) { focusSearch(binding) }
			}

			else -> Unit
		}
	}

	private fun focusSearch(binding: ItemMediaDiscoverHeaderBinding) {
		val query = binding.editSearch.text?.toString()?.trim().orEmpty()
		if (query.isNotEmpty()) {
			router.openMainSearch(mediaType.name, query)
			return
		}
		binding.editSearch.requestFocus()
		activity?.window?.let { window ->
			WindowCompat.getInsetsController(window, binding.editSearch).show(WindowInsetsCompat.Type.ime())
		}
	}

	private fun openLibrary(type: MediaType) {
		startActivity(Intent(requireContext(), UnifiedLibraryActivity::class.java)
			.putExtra(UnifiedLibraryActivity.EXTRA_MEDIA_KIND, type.name))
	}

	private fun openExploreTab() {
		startActivity(
			Intent(requireContext(), MainActivity::class.java)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
				.putExtra(MainActivity.EXTRA_OPEN_EXPLORE, true),
		)
		// Inside the main screen this switches tabs; inside the stand-alone host it also closes the host.
		(activity as? MediaDiscoverActivity)?.finish()
	}

	private fun openTypedSearch(query: String) {
		startActivity(Intent(requireContext(), SearchActivity::class.java)
			.putExtra(AppRouter.KEY_QUERY, query)
			.putExtra(AppRouter.KEY_KIND, SearchKind.SIMPLE)
			.putExtra(SearchActivity.EXTRA_MEDIA_TYPE, mediaType.name))
	}

	private fun onItemClick(item: AniListDiscover.Item) {
		val manga = item.manga
		if (manga != null) {
			router.openDetails(manga)
		} else {
			openTypedSearch(item.title)
		}
	}

	/** Long-press on a cover: add it to the library, or search its alternative title. */
	private fun onItemLongClick(item: AniListDiscover.Item): Boolean {
		val actions = ArrayList<Pair<String, () -> Unit>>()
		actions.add(Pair<String, () -> Unit>(getString(R.string.discover_add_to_library), {
			addDiscoverItemToLibrary(item, mediaType, libraryFinder) { openTypedSearch(it) }
		}))
		item.alternativeTitle?.let { alternative ->
			actions.add(Pair<String, () -> Unit>(getString(R.string.discover_search_alternative), { openTypedSearch(alternative) }))
		}
		MaterialAlertDialogBuilder(requireContext())
			.setTitle(item.title)
			.setItems(actions.map { it.first }.toTypedArray()) { _, which -> actions[which].second() }
			.setNegativeButton(android.R.string.cancel, null)
			.show()
		return true
	}

	private class HeaderHolder(val binding: ItemMediaDiscoverHeaderBinding) : RecyclerView.ViewHolder(binding.root)

	/** The single header row: mascot, title, intro, search field and action chips. */
	private inner class HeaderAdapter : RecyclerView.Adapter<HeaderHolder>() {

		private var isEmptyMessageVisible = false

		fun showEmptyMessage(value: Boolean) {
			if (value != isEmptyMessageVisible) {
				isEmptyMessageVisible = value
				notifyItemChanged(0)
			}
		}

		override fun getItemCount() = 1

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeaderHolder {
			val holder = HeaderHolder(ItemMediaDiscoverHeaderBinding.inflate(LayoutInflater.from(parent.context), parent, false))
			val binding = holder.binding
			Mascot.bind(binding.imageMascot, Mascot.formFor(mediaType))
			if (mediaType == MediaType.VIDEO) {
				binding.textTitle.setText(R.string.media_discover_video_title)
				binding.textIntro.setText(R.string.media_discover_video_intro)
				binding.layoutSearch.setHint(R.string.media_discover_video_search_hint)
			} else {
				binding.textTitle.setText(R.string.media_discover_books_title)
				binding.textIntro.setText(R.string.media_discover_books_intro)
				binding.layoutSearch.setHint(R.string.media_discover_books_search_hint)
			}
			binding.layoutSearch.setStartIconDrawable(androidx.appcompat.R.drawable.abc_ic_search_api_material)
			binding.editSearch.setOnEditorActionListener { view, actionId, _ ->
				if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE) {
					val query = view.text?.toString()?.trim().orEmpty()
					if (query.isNotEmpty()) router.openMainSearch(mediaType.name, query)
					true
				} else {
					false
				}
			}
			binding.textMessage.setOnClickListener { viewModel.reload() }
			bindActions(binding)
			return holder
		}

		override fun onBindViewHolder(holder: HeaderHolder, position: Int) {
			holder.binding.textMessage.isVisible = isEmptyMessageVisible
		}
	}

	private inner class ShelfHolder(val binding: ItemMediaDiscoverShelfBinding) : RecyclerView.ViewHolder(binding.root) {

		val covers = CoversAdapter()
		var boundKey: String? = null

		init {
			binding.recyclerCarousel.layoutManager =
				LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
			binding.recyclerCarousel.setRecycledViewPool(carouselPool)
			binding.recyclerCarousel.adapter = covers
		}
	}

	private inner class ShelvesAdapter : ListAdapter<MediaDiscoverShelfModel, ShelfHolder>(ShelfDiff) {

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			ShelfHolder(ItemMediaDiscoverShelfBinding.inflate(LayoutInflater.from(parent.context), parent, false))

		override fun onBindViewHolder(holder: ShelfHolder, position: Int) {
			val model = getItem(position)
			val binding = holder.binding
			binding.textTitle.setText(model.shelf.title)
			val loaded = model.state as? MediaDiscoverShelfState.Loaded
			binding.progress.isVisible = model.state is MediaDiscoverShelfState.Loading
			val source = model.shelf.source
			binding.buttonSeeAll.isVisible = source != null && loaded != null
			binding.buttonSeeAll.setOnClickListener {
				if (source != null) router.openList(source, null, null)
			}
			holder.covers.setItems(loaded?.items)
			if (holder.boundKey != model.shelf.key) {
				holder.boundKey = model.shelf.key
				binding.recyclerCarousel.scrollToPosition(0)
			}
		}
	}

	private class CoverHolder(val binding: ItemDiscoverBinding) : RecyclerView.ViewHolder(binding.root)

	/** Covers of one shelf; with no items yet it shows blank placeholder cards while the shelf loads. */
	private inner class CoversAdapter : RecyclerView.Adapter<CoverHolder>() {

		private var items: List<AniListDiscover.Item>? = null

		fun setItems(value: List<AniListDiscover.Item>?) {
			if (value == items) return
			items = value
			@Suppress("NotifyDataSetChanged")
			notifyDataSetChanged()
		}

		override fun getItemCount() = items?.size ?: resources.getInteger(R.integer.media_discover_placeholder_count)

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CoverHolder {
			val binding = ItemDiscoverBinding.inflate(LayoutInflater.from(parent.context), parent, false)
			binding.root.updateLayoutParams<ViewGroup.LayoutParams> {
				width = parent.resources.getDimensionPixelSize(R.dimen.media_discover_card_width)
			}
			binding.imageCover.setBackgroundColor(
				MaterialColors.getColor(binding.imageCover, materialR.attr.colorSurfaceContainerHighest),
			)
			return CoverHolder(binding)
		}

		override fun onBindViewHolder(holder: CoverHolder, position: Int) {
			val binding = holder.binding
			val item = items?.getOrNull(position)
			binding.imageCover.setImageDrawable(null)
			if (item == null) {
				binding.textTitle.text = null
				binding.textScore.isVisible = false
				binding.root.setOnClickListener(null)
				binding.root.setOnLongClickListener(null)
				binding.root.isClickable = false
				binding.root.isLongClickable = false
				return
			}
			binding.textTitle.text = item.title
			val badge = listOfNotNull(
				item.providerLabel?.takeIf { item.manga == null },
				item.score?.let { getString(R.string.media_discover_score_badge, it) },
				if (item.isAdult) getString(R.string.media_discover_adult_badge) else null,
			)
			binding.textScore.isVisible = badge.isNotEmpty()
			binding.textScore.text = badge.joinToString(" · ")
			item.coverUrl?.let { url ->
				coil.enqueue(
					ImageRequest.Builder(holder.itemView.context)
						.data(url)
						.crossfade(true)
						.target(binding.imageCover)
						.build(),
				)
			}
			binding.root.setOnClickListener { onItemClick(item) }
			binding.root.setOnLongClickListener { onItemLongClick(item) }
		}
	}

	private object ShelfDiff : DiffUtil.ItemCallback<MediaDiscoverShelfModel>() {

		override fun areItemsTheSame(oldItem: MediaDiscoverShelfModel, newItem: MediaDiscoverShelfModel) =
			oldItem.shelf.key == newItem.shelf.key

		override fun areContentsTheSame(oldItem: MediaDiscoverShelfModel, newItem: MediaDiscoverShelfModel) =
			oldItem == newItem
	}
}
