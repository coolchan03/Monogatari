package org.koitharu.kotatsu.search.ui.multi

import android.os.Build
import android.content.Intent
import android.text.InputType
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.MenuProvider
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.media.MediaType
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.search.domain.SearchKind
import org.koitharu.kotatsu.search.ui.suggestion.MainSearchScope

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
		menu.findItem(R.id.action_search_kind)?.subMenu?.findItem(R.id.action_toggle_preview)?.apply {
			isVisible = activity.canTogglePreview()
			setTitle(if (activity.isPreviewCollapsed()) R.string.search_preview_expand else R.string.search_preview_collapse)
		}
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

			R.id.action_filter_metadata -> {
				SearchMetadataFilterDialog.show(activity, viewModel)
				return true
			}

			R.id.action_toggle_preview -> {
				activity.togglePreview()
				return true
			}

			R.id.action_pane_layout -> {
				activity.showPaneLayoutDialog()
				return true
			}
            R.id.action_filter_media -> {
                showMediaScopeDialog()
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
			// In place: the query, filters, media type and the selected preview all stay.
			viewModel.setKind(newKind)
			activity.onSearchKindChanged()
		}
		return true
	}

	/** Same shared search type as the chips; switching restarts only the search, not the screen. */
	private fun showMediaScopeDialog() {
		val scopes = MainSearchScope.scopes
		MaterialAlertDialogBuilder(activity)
			.setTitle(R.string.search_media_type)
			.setSingleChoiceItems(
				scopes.map { MainSearchScope.label(activity, it) }.toTypedArray(),
				scopes.indexOf(viewModel.selectedMediaScope).coerceAtLeast(0),
			) { dialog, index ->
				dialog.dismiss()
				viewModel.setMediaScope(scopes[index])
			}.setNegativeButton(android.R.string.cancel, null).show()
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
		val unknownBox = android.widget.CheckBox(activity).apply {
			setText(R.string.publication_year_include_unknown)
			isChecked = viewModel.includesUnknownYears
		}
		container.addView(unknownBox)
		val languages = listOf<String?>(null) + viewModel.languagesInResults()
		val languagePicker = SearchDropdownField(
			activity, R.string.search_language_label,
			languages.map { code -> code?.let { java.util.Locale(it).getDisplayLanguage() } ?: activity.getString(R.string.search_language_any) },
			languages.indexOf(viewModel.selectedLanguage).coerceAtLeast(0),
		)
		container.addView(languagePicker.view)
		val ordering = PublicationYearOrder.entries.toList()
		val sortPicker = SearchDropdownField(
			activity, R.string.publication_year_sort_label,
			listOf(
				activity.getString(R.string.publication_year_sort_relevance),
				activity.getString(R.string.publication_year_sort_ascending),
				activity.getString(R.string.publication_year_sort_descending),
				activity.getString(R.string.publication_year_sort_ascending_global),
				activity.getString(R.string.publication_year_sort_descending_global),
			),
			ordering.indexOf(viewModel.publicationYearOrder).coerceAtLeast(0),
		)
		container.addView(sortPicker.view)
		container.addView(TextView(activity).apply {
			setText(R.string.publication_year_sort_hint)
			setPadding(0, (6 * density).toInt(), 0, 0)
		})
		MaterialAlertDialogBuilder(activity)
			.setTitle(R.string.publication_years)
			.setView(container)
			.setNeutralButton(R.string.any) { _, _ ->
				viewModel.setPublicationYearOrder(PublicationYearOrder.RELEVANCE)
				viewModel.setIncludeUnknownYears(true)
				viewModel.setLanguageFilter(null)
				viewModel.setPublicationYearRange(null, null)
			}
			.setNegativeButton(android.R.string.cancel, null)
			.setPositiveButton(android.R.string.ok) { _, _ ->
				viewModel.setIncludeUnknownYears(unknownBox.isChecked)
				viewModel.setLanguageFilter(languages[languagePicker.selectedIndex])
				viewModel.setPublicationYearOrder(ordering[sortPicker.selectedIndex])
				viewModel.setPublicationYearRange(
					from.text?.toString()?.trim()?.toIntOrNull(),
					to.text?.toString()?.trim()?.toIntOrNull(),
				)
			}
			.show()
	}
}
