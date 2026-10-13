package org.koitharu.kotatsu.discover

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.target
import com.google.android.material.chip.Chip
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.databinding.ActivityDiscoverBinding
import org.koitharu.kotatsu.databinding.ItemDiscoverBinding
import javax.inject.Inject

/** AniList trending/popular lists, including Boys' Love; a tap searches every enabled source for the title. */
@AndroidEntryPoint
class DiscoverActivity : AppCompatActivity() {

	@Inject
	lateinit var discover: AniListDiscover

	@Inject
	lateinit var coil: ImageLoader

	private lateinit var binding: ActivityDiscoverBinding
	private val adapter = ItemsAdapter()
	private var feed = AniListDiscover.Feed.TRENDING_BL
	private var nextPage = 1
	private var hasNextPage = true
	private var loadJob: Job? = null

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		binding = ActivityDiscoverBinding.inflate(layoutInflater)
		setContentView(binding.root)
		setTitle(R.string.discover)
		supportActionBar?.setDisplayHomeAsUpEnabled(true)
		ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
			// Edge-to-edge (enforced on recent Android): keep content clear of the system bars.
			val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
			view.updatePadding(
				left = bars.left,
				right = bars.right,
				top = if (supportActionBar == null) bars.top else 0,
				bottom = bars.bottom,
			)
			insets
		}

		savedInstanceState?.getString(KEY_FEED)?.let { name ->
			AniListDiscover.Feed.entries.find { it.name == name }?.let { feed = it }
		}
		for (entry in AniListDiscover.Feed.entries) {
			val chip = Chip(this).apply {
				id = entry.ordinal + 1
				setText(entry.titleRes)
				isCheckable = true
				isChecked = entry == feed
			}
			binding.chipsFeed.addView(chip)
		}
		binding.chipsFeed.setOnCheckedStateChangeListener { _, checkedIds ->
			val selected = checkedIds.firstOrNull()?.let { AniListDiscover.Feed.entries.getOrNull(it - 1) }
			if (selected != null && selected != feed) {
				feed = selected
				reload()
			}
		}

		val layoutManager = GridLayoutManager(this, resources.getInteger(R.integer.discover_columns))
		binding.recyclerView.layoutManager = layoutManager
		binding.recyclerView.adapter = adapter
		binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
			override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
				if (dy > 0 && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 6) {
					loadMore()
				}
			}
		})
		binding.textMessage.setOnClickListener { if (adapter.itemCount == 0) reload() }
		reload()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString(KEY_FEED, feed.name)
	}

	override fun onSupportNavigateUp(): Boolean {
		finish()
		return true
	}

	private fun reload() {
		loadJob?.cancel()
		loadJob = null
		adapter.items = emptyList()
		nextPage = 1
		hasNextPage = true
		loadMore()
	}

	private fun loadMore() {
		if (loadJob?.isActive == true || !hasNextPage) return
		val requestedFeed = feed
		val page = nextPage
		binding.progress.isVisible = true
		binding.textMessage.isVisible = false
		loadJob = lifecycleScope.launch {
			try {
				val result = discover.load(requestedFeed, page)
				if (requestedFeed != feed) return@launch
				adapter.items = adapter.items + result.items.filter { new -> adapter.items.none { it.id == new.id } }
				hasNextPage = result.hasNextPage && result.items.isNotEmpty()
				nextPage = page + 1
				if (adapter.itemCount == 0) {
					showMessage(getString(R.string.nothing_found))
				}
			} catch (e: CancellationException) {
				throw e
			} catch (e: Exception) {
				if (adapter.itemCount == 0) {
					showMessage(getString(R.string.discover_load_failed, e.message.orEmpty()))
				}
			} finally {
				binding.progress.isVisible = false
			}
		}
	}

	private fun showMessage(text: String) {
		binding.textMessage.text = text
		binding.textMessage.isVisible = true
	}

	private class Holder(val binding: ItemDiscoverBinding) : RecyclerView.ViewHolder(binding.root)

	private inner class ItemsAdapter : RecyclerView.Adapter<Holder>() {

		var items: List<AniListDiscover.Item> = emptyList()
			set(value) {
				field = value
				@Suppress("NotifyDataSetChanged")
				notifyDataSetChanged()
			}

		override fun getItemCount() = items.size

		override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
			Holder(ItemDiscoverBinding.inflate(LayoutInflater.from(parent.context), parent, false))

		override fun onBindViewHolder(holder: Holder, position: Int) {
			val item = items[position]
			holder.binding.textTitle.text = item.title
			val badge = listOfNotNull(item.score?.let { "$it%" }, if (item.isAdult) "18+" else null)
			holder.binding.textScore.isVisible = badge.isNotEmpty()
			holder.binding.textScore.text = badge.joinToString(" · ")
			holder.binding.imageCover.setImageDrawable(null)
			item.coverUrl?.let { url ->
				coil.enqueue(
					ImageRequest.Builder(holder.itemView.context)
						.data(url)
						.crossfade(true)
						.target(holder.binding.imageCover)
						.build(),
				)
			}
			holder.binding.root.setOnClickListener {
				// Search every enabled source for this title; the English title is tried first.
				router.openSearch(item.title)
			}
			holder.binding.root.setOnLongClickListener {
				val alternative = item.alternativeTitle ?: return@setOnLongClickListener false
				router.openSearch(alternative)
				true
			}
		}
	}

	private companion object {

		const val KEY_FEED = "feed"
	}
}
