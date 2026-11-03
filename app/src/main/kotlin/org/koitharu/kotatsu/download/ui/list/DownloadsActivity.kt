package org.koitharu.kotatsu.download.ui.list

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.view.ActionMode
import androidx.core.graphics.Insets
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import coil3.ImageLoader
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.BaseActivity
import org.koitharu.kotatsu.core.ui.list.ListSelectionController
import org.koitharu.kotatsu.core.ui.list.RecyclerScrollKeeper
import org.koitharu.kotatsu.core.ui.util.MenuInvalidator
import org.koitharu.kotatsu.core.ui.util.ReversibleActionObserver
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.textAndVisible
import org.koitharu.kotatsu.databinding.ActivityDownloadsBinding
import org.koitharu.kotatsu.download.ui.worker.DownloadWorker
import org.koitharu.kotatsu.list.ui.adapter.ListStateHolderListener
import org.koitharu.kotatsu.list.ui.adapter.TypedListSpacingDecoration
import javax.inject.Inject

@AndroidEntryPoint
class DownloadsActivity : BaseActivity<ActivityDownloadsBinding>(),
	DownloadItemListener,
	ListStateHolderListener,
	ListSelectionController.Callback {

	@Inject
	lateinit var coil: ImageLoader

	@Inject
	lateinit var scheduler: DownloadWorker.Scheduler

	private val viewModel by viewModels<DownloadsViewModel>()
	private lateinit var selectionController: ListSelectionController

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(ActivityDownloadsBinding.inflate(layoutInflater))
		setDisplayHomeAsUp(isEnabled = true, showUpAsClose = false)
		val downloadsAdapter = DownloadsAdapter(this, this, this)
		val decoration = TypedListSpacingDecoration(this, false)
		selectionController = ListSelectionController(
			appCompatDelegate = delegate,
			decoration = DownloadsSelectionDecoration(this),
			registryOwner = this,
			callback = this,
		)
		with(viewBinding.recyclerView) {
			setHasFixedSize(true)
			addItemDecoration(decoration)
			adapter = downloadsAdapter
			selectionController.attachToRecyclerView(this)
			RecyclerScrollKeeper(this).attach()
		}
		addMenuProvider(DownloadsMenuProvider(this, viewModel))
		viewModel.items.observe(this, downloadsAdapter)
		viewModel.onActionDone.observeEvent(this, ReversibleActionObserver(viewBinding.recyclerView))
		val menuInvalidator = MenuInvalidator(this)
		viewModel.hasActiveWorks.observe(this, menuInvalidator)
		viewModel.hasPausedWorks.observe(this, menuInvalidator)
		viewModel.hasCancellableWorks.observe(this, menuInvalidator)
		viewModel.hasFailedWorks.observe(this, menuInvalidator)
		viewBinding.buttonNetwork.setOnClickListener { router.openDownloadsSetting() }
		viewModel.summary.observe(this) { bindSummary(it) }
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
		viewBinding.recyclerView.updatePadding(
			left = bars.left,
			right = bars.right,
			bottom = bars.bottom,
		)
		viewBinding.appbar.updatePadding(
			left = bars.left,
			right = bars.right,
			top = bars.top,
		)
		return WindowInsetsCompat.Builder(insets)
			.setInsets(WindowInsetsCompat.Type.systemBars(), Insets.NONE)
			.build()
	}

	private fun bindSummary(summary: DownloadsSummary) {
		viewBinding.cardSummary.isVisible = summary.isVisible
		if (!summary.isVisible) {
			return
		}
		val parts = ArrayList<String>(4)
		if (summary.downloading > 0) parts += getString(R.string.dlq_summary_downloading, summary.downloading)
		if (summary.queued > 0) parts += getString(R.string.dlq_summary_queued, summary.queued)
		if (summary.paused > 0) parts += getString(R.string.dlq_summary_paused, summary.paused)
		if (summary.failed > 0) parts += getString(R.string.dlq_summary_failed, summary.failed)
		viewBinding.textViewSummary.text = parts.joinToString(" \u00b7 ")
		viewBinding.textViewSummaryEta.textAndVisible = summary.eta?.let { formatEta(it) }
	}

	private fun formatEta(eta: DownloadEta): String = when (eta.unit) {
		DownloadEta.Scale.LESS_THAN_MINUTE -> getString(R.string.dlq_eta_less_than_minute)
		DownloadEta.Scale.MINUTES -> getString(
			R.string.dlq_eta_left,
			resources.getQuantityString(R.plurals.dlq_eta_minutes, eta.amount, eta.amount),
		)

		DownloadEta.Scale.HOURS -> getString(
			R.string.dlq_eta_left,
			resources.getQuantityString(R.plurals.dlq_eta_hours, eta.amount, eta.amount),
		)
	}

	override fun onRetryClick(error: Throwable) = Unit

	override fun onEmptyActionClick() {
		// Auto-download lives one tap inside the Downloads settings screen
		router.openDownloadsSetting()
	}

	override fun onItemClick(item: DownloadItemModel, view: View) {
		if (selectionController.onItemClick(item.id.mostSignificantBits)) {
			return
		}
		router.openDetails(item.manga ?: return)
	}

	override fun onItemLongClick(item: DownloadItemModel, view: View): Boolean {
		return selectionController.onItemLongClick(view, item.id.mostSignificantBits)
	}

	override fun onItemContextClick(item: DownloadItemModel, view: View): Boolean {
		return selectionController.onItemContextClick(view, item.id.mostSignificantBits)
	}

	override fun onExpandClick(item: DownloadItemModel) {
		if (!selectionController.onItemClick(item.id.mostSignificantBits)) {
			viewModel.expandCollapse(item)
		}
	}

	override fun onCancelClick(item: DownloadItemModel) {
		viewModel.cancel(item.id)
	}

	override fun onPauseClick(item: DownloadItemModel) {
		scheduler.pause(item.id)
	}

	override fun onResumeClick(item: DownloadItemModel) {
		scheduler.resume(item.id)
	}

	override fun onSkipClick(item: DownloadItemModel) {
		scheduler.skip(item.id)
	}

	override fun onSkipAllClick(item: DownloadItemModel) {
		scheduler.skipAll(item.id)
	}

	override fun onSelectionChanged(controller: ListSelectionController, count: Int) {
		viewBinding.recyclerView.invalidateItemDecorations()
	}

	override fun onCreateActionMode(
		controller: ListSelectionController,
		menuInflater: MenuInflater,
		menu: Menu
	): Boolean {
		menuInflater.inflate(R.menu.mode_downloads, menu)
		return true
	}

	override fun onActionItemClicked(controller: ListSelectionController, mode: ActionMode?, item: MenuItem): Boolean {
		return when (item.itemId) {
			R.id.action_resume -> {
				viewModel.resume(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_pause -> {
				viewModel.pause(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_cancel -> {
				viewModel.cancel(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_remove -> {
				viewModel.remove(controller.snapshot())
				mode?.finish()
				true
			}

			R.id.action_select_all -> {
				controller.addAll(viewModel.allIds())
				true
			}

			else -> false
		}
	}

	override fun onPrepareActionMode(controller: ListSelectionController, mode: ActionMode?, menu: Menu): Boolean {
		val snapshot = viewModel.snapshot(controller.peekCheckedIds())
		var canPause = true
		var canResume = true
		var canCancel = true
		var canRemove = true
		for (item in snapshot) {
			canPause = canPause and item.canPause
			canResume = canResume and item.canResume
			canCancel = canCancel and !item.workState.isFinished
			canRemove = canRemove and item.workState.isFinished
		}
		menu.findItem(R.id.action_pause)?.isVisible = canPause
		menu.findItem(R.id.action_resume)?.isVisible = canResume
		menu.findItem(R.id.action_cancel)?.isVisible = canCancel
		menu.findItem(R.id.action_remove)?.isVisible = canRemove
		return super.onPrepareActionMode(controller, mode, menu)
	}
}
