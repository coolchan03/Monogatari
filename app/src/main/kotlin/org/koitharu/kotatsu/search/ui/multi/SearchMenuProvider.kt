package org.koitharu.kotatsu.search.ui.multi

import android.os.Build
import android.content.Intent
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.search.domain.SearchKind

class SearchMenuProvider(
	private val activity: SearchActivity,
	private val viewModel: SearchViewModel,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_search_kind, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		super.onPrepareMenu(menu)
		menu.findItem(
			when (viewModel.kind) {
				SearchKind.SIMPLE -> R.id.action_kind_simple
				SearchKind.TITLE -> R.id.action_kind_title
				SearchKind.AUTHOR -> R.id.action_kind_author
				SearchKind.TAG -> R.id.action_kind_tag
			},
		)?.isChecked = true
		menu.findItem(R.id.action_filter_hide_empty)?.isChecked = viewModel.isHideEmptyEnabled
		menu.findItem(R.id.action_filter_pinned_only)?.isChecked = viewModel.isPinnedOnly
        menu.findItem(R.id.action_search_kind)?.subMenu
            ?.setGroupVisible(R.id.group_video_search_modes, viewModel.isVideoModeSearch)
        menu.findItem(
            when (viewModel.videoResultMode) {
                VideoResultMode.VIDEOS -> R.id.action_video_results
                VideoResultMode.CHANNELS -> R.id.action_channel_results
                VideoResultMode.PLAYLISTS -> R.id.action_playlist_results
            },
        )?.isChecked = true

	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
        when (menuItem.itemId) {
            R.id.action_video_results, R.id.action_channel_results, R.id.action_playlist_results -> {
                val mode = when (menuItem.itemId) {
                    R.id.action_channel_results -> VideoResultMode.CHANNELS
                    R.id.action_playlist_results -> VideoResultMode.PLAYLISTS
                    else -> VideoResultMode.VIDEOS
                }
                menuItem.isChecked = true
                viewModel.setVideoResultMode(mode)
                return true
            }
            R.id.action_filter_pinned_only -> {
				menuItem.isChecked = !menuItem.isChecked
				viewModel.setPinnedOnly(menuItem.isChecked)
				return true
			}

			R.id.action_filter_hide_empty -> {
				menuItem.isChecked = !menuItem.isChecked
				viewModel.setHideEmpty(menuItem.isChecked)
				return true
			}

			R.id.action_filter_years -> {
				showPublicationYearsDialog()
				return true
			}
		}

		val newKind = when (menuItem.itemId) {
			R.id.action_kind_simple -> SearchKind.SIMPLE
			R.id.action_kind_title -> SearchKind.TITLE
			R.id.action_kind_author -> SearchKind.AUTHOR
			R.id.action_kind_tag -> SearchKind.TAG
			else -> return false
		}
		if (newKind != viewModel.kind) {
			if (viewModel.mediaTypeOverride == null) {
				activity.router.openSearch(viewModel.query, newKind)
			} else {
				activity.startActivity(Intent(activity, SearchActivity::class.java)
					.putExtra(AppRouter.KEY_QUERY, viewModel.query)
					.putExtra(AppRouter.KEY_KIND, newKind)
					.putExtra(SearchActivity.EXTRA_MEDIA_TYPE, viewModel.mediaTypeOverride?.name))
			}
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
				activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out, 0)
			} else {
				activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
			}
			activity.finishAfterTransition()
		}
		return true
	}

	private fun showPublicationYearsDialog() {
		val density = activity.resources.displayMetrics.density
		val padding = (24 * density).toInt()
		val container = LinearLayout(activity).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(padding, 0, padding, 0)
		}
		val hint = TextView(activity).apply {
			setText(R.string.publication_year_filter_hint)
			setPadding(0, 0, 0, (8 * density).toInt())
		}
		val from = EditText(activity).apply {
			inputType = InputType.TYPE_CLASS_NUMBER
			setHint(R.string.publication_year_from)
			setText(viewModel.publicationYearFrom?.toString().orEmpty())
		}
		val to = EditText(activity).apply {
			inputType = InputType.TYPE_CLASS_NUMBER
			setHint(R.string.publication_year_to)
			setText(viewModel.publicationYearTo?.toString().orEmpty())
		}
		container.addView(hint)
		container.addView(from)
		container.addView(to)
		val ordering = PublicationYearOrder.entries.toList()
		container.addView(TextView(activity).apply {
			setText(R.string.publication_year_sort_label)
			setPadding(0, (12 * density).toInt(), 0, (4 * density).toInt())
		})
		val sortPicker = Spinner(activity).apply {
			adapter = ArrayAdapter(
				activity, android.R.layout.simple_spinner_item,
				listOf(
					activity.getString(R.string.publication_year_sort_relevance),
					activity.getString(R.string.publication_year_sort_ascending),
					activity.getString(R.string.publication_year_sort_descending),
				),
			).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
			setSelection(ordering.indexOf(viewModel.publicationYearOrder).coerceAtLeast(0))
		}
		container.addView(sortPicker)
		container.addView(TextView(activity).apply {
			setText(R.string.publication_year_sort_hint)
			setPadding(0, (6 * density).toInt(), 0, 0)
		})
		MaterialAlertDialogBuilder(activity)
			.setTitle(R.string.publication_years)
			.setView(container)
			.setNeutralButton(R.string.any) { _, _ ->
				viewModel.setPublicationYearOrder(PublicationYearOrder.RELEVANCE)
				viewModel.setPublicationYearRange(null, null)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				viewModel.setPublicationYearOrder(ordering[sortPicker.selectedItemPosition])
				viewModel.setPublicationYearRange(
					from.text?.toString()?.trim()?.toIntOrNull(),
					to.text?.toString()?.trim()?.toIntOrNull(),
				)
			}
			.show()
	}
}
