package org.koitharu.kotatsu.sync.conflict

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.addBodyText
import org.koitharu.kotatsu.core.ui.addHeaderCard
import org.koitharu.kotatsu.core.ui.addPrimaryButton
import org.koitharu.kotatsu.core.ui.addSecondaryButton
import org.koitharu.kotatsu.core.ui.addSectionCard
import org.koitharu.kotatsu.core.ui.buttonParams
import org.koitharu.kotatsu.core.ui.dp
import org.koitharu.kotatsu.core.ui.pageColumn
import org.koitharu.kotatsu.core.ui.scrollable
import com.google.android.material.R as materialR

/**
 * Second review sheet of a Nearby merge, shown after the history sheet: titles whose favourites categories differ
 * between the devices. Each title offers "Keep this" / "Take other" / "Keep both" (union of the categories). Nothing is
 * merged until the user applies; cancelling discards the received library.
 */
object FavouriteConflictSheet {

	/** Rows beyond this follow the "for all" choice, so a first sync with hundreds of titles stays usable. */
	private const val MAX_ROWS = 150

	fun show(
		context: Context,
		conflicts: List<FavouriteConflicts.Conflict>,
		onApply: (FavouriteConflicts.Resolution) -> Unit,
		onCancel: () -> Unit,
	) {
		val choices = FavouriteConflicts.newestForAll(conflicts).toMutableMap()
		// Per title: the toggle group and the ids of its (keep this, take other, keep both) buttons.
		val toggles = HashMap<Long, Pair<MaterialButtonToggleGroup, IntArray>>()
		val dialog = BottomSheetDialog(context)
		val body = context.pageColumn()
		body.addHeaderCard(
			MascotForm.DEFAULT,
			context.getString(R.string.nearby_fav_conflict_title),
			context.resources.getQuantityString(R.plurals.nearby_fav_conflict_summary, conflicts.size, conflicts.size),
		)

		fun applyAll(all: Map<Long, FavouriteConflicts.Choice>) {
			choices.clear()
			choices.putAll(all)
			toggles.forEach { (id, toggle) ->
				val (group, ids) = toggle
				group.check(ids[indexOf(choices[id])])
			}
		}

		val bulk = body.addSectionCard(context.getString(R.string.sync_conflict_for_all))
		bulk.addSecondaryButton(context.getString(R.string.sync_conflict_newest_for_all)) {
			applyAll(FavouriteConflicts.newestForAll(conflicts))
		}
		bulk.addSecondaryButton(context.getString(R.string.sync_conflict_local_for_all)) {
			applyAll(FavouriteConflicts.keepLocalForAll(conflicts))
		}
		bulk.addSecondaryButton(context.getString(R.string.nearby_fav_keep_both_for_all)) {
			applyAll(FavouriteConflicts.keepBothForAll(conflicts))
		}

		for (conflict in conflicts.take(MAX_ROWS)) {
			val card = body.addSectionCard(conflict.title)
			card.addBodyText(context.getString(R.string.sync_conflict_this_device, describe(context, conflict.local)))
			card.addBodyText(context.getString(R.string.sync_conflict_other_device, describe(context, conflict.incoming)))
			val ids = intArrayOf(View.generateViewId(), View.generateViewId(), View.generateViewId())
			val group = MaterialButtonToggleGroup(context).apply {
				isSingleSelection = true
				isSelectionRequired = true
			}
			group.addView(toggleButton(context, ids[0], R.string.sync_conflict_keep_this), toggleParams())
			group.addView(toggleButton(context, ids[1], R.string.sync_conflict_take_other), toggleParams())
			group.addView(toggleButton(context, ids[2], R.string.nearby_fav_keep_both), toggleParams())
			group.check(ids[indexOf(choices[conflict.mangaId])])
			group.addOnButtonCheckedListener { _, checkedId, isChecked ->
				if (isChecked) {
					choices[conflict.mangaId] = when (checkedId) {
						ids[0] -> FavouriteConflicts.Choice.KEEP_LOCAL
						ids[1] -> FavouriteConflicts.Choice.TAKE_INCOMING
						else -> FavouriteConflicts.Choice.KEEP_BOTH
					}
				}
			}
			card.addView(group, context.buttonParams())
			toggles[conflict.mangaId] = group to ids
		}
		val hidden = conflicts.size - MAX_ROWS
		if (hidden > 0) {
			body.addBodyText(context.resources.getQuantityString(R.plurals.sync_conflict_more, hidden, hidden))
		}

		body.addPrimaryButton(context.getString(R.string.sync_conflict_apply)) {
			dialog.dismiss()
			onApply(FavouriteConflicts.resolve(conflicts, choices))
		}
		body.addSecondaryButton(context.getString(R.string.sync_conflict_cancel)) {
			dialog.dismiss()
			onCancel()
		}

		dialog.setContentView(body.scrollable())
		dialog.setCancelable(false)
		dialog.behavior.skipCollapsed = true
		dialog.behavior.state = BottomSheetBehavior.STATE_EXPANDED
		dialog.show()
	}

	private fun indexOf(choice: FavouriteConflicts.Choice?): Int = when (choice) {
		FavouriteConflicts.Choice.KEEP_LOCAL -> 0
		FavouriteConflicts.Choice.KEEP_BOTH -> 2
		else -> 1
	}

	/** "Reading, Favourites", or a placeholder when the title is in no category on that device. */
	private fun describe(context: Context, snapshot: FavouriteConflicts.Snapshot): String =
		if (snapshot.categories.isEmpty()) {
			context.getString(R.string.nearby_fav_none)
		} else {
			snapshot.categories.joinToString(", ")
		}

	private fun toggleButton(context: Context, id: Int, label: Int): MaterialButton =
		MaterialButton(context, null, materialR.attr.materialButtonOutlinedStyle).apply {
			this.id = id
			setText(label)
			minHeight = context.dp(48)
		}

	private fun toggleParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
}
