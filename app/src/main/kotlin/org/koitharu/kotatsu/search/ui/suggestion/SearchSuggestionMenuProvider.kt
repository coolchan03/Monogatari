package org.koitharu.kotatsu.search.ui.suggestion

import android.content.Context
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.activity.result.ActivityResultLauncher
import androidx.core.view.MenuProvider
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.util.ext.resolve
import org.koitharu.kotatsu.core.util.ext.tryLaunch

class SearchSuggestionMenuProvider(
	private val context: Context,
	private val voiceInputLauncher: ActivityResultLauncher<String?>,
	private val viewModel: SearchSuggestionViewModel,
	/** Called after the shared search type was changed here, so the bar's hint can follow. */
	private val onSearchScopeChanged: () -> Unit = {},
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_search_suggestion, menu)
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		return when (menuItem.itemId) {
			R.id.action_clear -> {
				clearSearchHistory()
				true
			}

			R.id.action_voice_search -> {
				voiceInputLauncher.tryLaunch(context.getString(R.string.search_manga), null)
			}

			R.id.action_search_type -> {
				chooseSearchType(menuItem)
				true
			}

			else -> false
		}
	}

	override fun onPrepareMenu(menu: Menu) {
		super.onPrepareMenu(menu)
		menu.findItem(R.id.action_voice_search)?.isVisible = voiceInputLauncher.resolve(context, null) != null
		menu.findItem(R.id.action_search_type)?.let { updateSearchTypeTitle(it) }
	}

	/** Manga, books, video or audio only: the main search bar then queries just those sources. */
	private fun chooseSearchType(menuItem: MenuItem) {
		val scopes = MainSearchScope.scopes
		val current = scopes.indexOf(MainSearchScope.current(context)).coerceAtLeast(0)
		buildAlertDialog(context, isCentered = true) {
			setTitle(R.string.search_media_type)
			setIcon(R.drawable.ic_filter_menu)
			setSingleChoiceItems(scopes.map { MainSearchScope.label(context, it) }.toTypedArray(), current) { dialog, which ->
				MainSearchScope.set(context, scopes[which])
				updateSearchTypeTitle(menuItem)
				onSearchScopeChanged()
				dialog.dismiss()
			}
			setNegativeButton(android.R.string.cancel, null)
		}.show()
	}

	private fun updateSearchTypeTitle(menuItem: MenuItem) {
		menuItem.title = context.getString(
			R.string.main_search_type_title,
			MainSearchScope.label(context, MainSearchScope.current(context)),
		)
	}

	private fun clearSearchHistory() {
		buildAlertDialog(context, isCentered = true) {
			setTitle(R.string.clear_search_history)
			setIcon(R.drawable.ic_clear_all)
			setCancelable(true)
			setMessage(R.string.text_clear_search_history_prompt)
			setNegativeButton(android.R.string.cancel, null)
			setPositiveButton(R.string.clear) { _, _ -> viewModel.clearSearchHistory() }
		}.show()
	}
}
