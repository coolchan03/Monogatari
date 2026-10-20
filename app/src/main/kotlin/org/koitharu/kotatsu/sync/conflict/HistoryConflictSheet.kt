package org.koitharu.kotatsu.sync.conflict

import android.content.Context
import android.text.format.DateUtils
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
import org.koitharu.kotatsu.list.domain.ReadingProgress
import com.google.android.material.R as materialR

/**
 * Bottom sheet listing reading-progress conflicts from a Nearby merge. Each title offers "Keep this" / "Take other";
 * "Keep newest for all" (the default) and "Keep this device for all" set every title at once. Nothing is merged
 * until the user taps apply; cancelling discards the received library.
 */
object HistoryConflictSheet {

	/** Rows beyond this follow the "for all" choice, so a first sync with hundreds of titles stays usable. */
	private const val MAX_ROWS = 150

	fun show(
		context: Context,
		review: HistoryConflictRepository.Review,
		onApply: (HistoryConflicts.Resolution) -> Unit,
		onCancel: () -> Unit,
	) {
		val conflicts = review.conflicts
		val choices = HistoryConflicts.newestForAll(conflicts).toMutableMap()
		val toggles = HashMap<Long, Pair<MaterialButtonToggleGroup, IntArray>>()
		val dialog = BottomSheetDialog(context)
		val body = context.pageColumn()
		body.addHeaderCard(
			MascotForm.DEFAULT,
			context.getString(R.string.sync_conflict_title),
			context.resources.getQuantityString(R.plurals.sync_conflict_summary, conflicts.size, conflicts.size),
		)

		fun applyAll(all: Map<Long, HistoryConflicts.Choice>) {
			choices.clear()
			choices.putAll(all)
			toggles.forEach { (id, toggle) ->
				val (group, ids) = toggle
				group.check(if (choices[id] == HistoryConflicts.Choice.KEEP_LOCAL) ids[0] else ids[1])
			}
		}

		val bulk = body.addSectionCard(context.getString(R.string.sync_conflict_for_all))
		bulk.addSecondaryButton(context.getString(R.string.sync_conflict_newest_for_all)) {
			applyAll(HistoryConflicts.newestForAll(conflicts))
		}
		bulk.addSecondaryButton(context.getString(R.string.sync_conflict_local_for_all)) {
			applyAll(HistoryConflicts.keepLocalForAll(conflicts))
		}

		val now = System.currentTimeMillis()
		for (conflict in conflicts.take(MAX_ROWS)) {
			val card = body.addSectionCard(conflict.title)
			card.addBodyText(context.getString(R.string.sync_conflict_this_device, describe(context, conflict.local, review, now)))
			card.addBodyText(context.getString(R.string.sync_conflict_other_device, describe(context, conflict.incoming, review, now)))
			val keepId = View.generateViewId()
			val takeId = View.generateViewId()
			val group = MaterialButtonToggleGroup(context).apply {
				isSingleSelection = true
				isSelectionRequired = true
			}
			group.addView(toggleButton(context, keepId, R.string.sync_conflict_keep_this), toggleParams())
			group.addView(toggleButton(context, takeId, R.string.sync_conflict_take_other), toggleParams())
			group.check(if (choices[conflict.mangaId] == HistoryConflicts.Choice.KEEP_LOCAL) keepId else takeId)
			group.addOnButtonCheckedListener { _, checkedId, isChecked ->
				if (isChecked) {
					choices[conflict.mangaId] = if (checkedId == keepId) {
						HistoryConflicts.Choice.KEEP_LOCAL
					} else {
						HistoryConflicts.Choice.TAKE_INCOMING
					}
				}
			}
			card.addView(group, context.buttonParams())
			toggles[conflict.mangaId] = group to intArrayOf(keepId, takeId)
		}
		val hidden = conflicts.size - MAX_ROWS
		if (hidden > 0) {
			body.addBodyText(context.resources.getQuantityString(R.plurals.sync_conflict_more, hidden, hidden))
		}

		body.addPrimaryButton(context.getString(R.string.sync_conflict_apply)) {
			dialog.dismiss()
			onApply(HistoryConflicts.resolve(conflicts, choices))
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

	/** "Ch 12 · 3 days ago", falling back to the read percentage or page when the chapter number is unknown. */
	private fun describe(
		context: Context,
		snapshot: HistoryConflicts.Snapshot,
		review: HistoryConflictRepository.Review,
		now: Long,
	): String {
		val position = when {
			snapshot.isDeleted -> context.getString(R.string.sync_conflict_removed)
			else -> HistoryConflicts.formatChapterNumber(review.chapterNumbers[snapshot.chapterId])
				?.let { context.getString(R.string.sync_conflict_chapter, it) }
				?: if (ReadingProgress.isValid(snapshot.percent)) {
					context.getString(R.string.sync_conflict_percent, (snapshot.percent * 100).toInt())
				} else {
					context.getString(R.string.sync_conflict_page, snapshot.page + 1)
				}
		}
		val time = DateUtils.getRelativeTimeSpanString(snapshot.modifiedAt, now, DateUtils.MINUTE_IN_MILLIS)
		return context.getString(R.string.sync_conflict_detail, position, time)
	}

	private fun toggleButton(context: Context, id: Int, label: Int): MaterialButton =
		MaterialButton(context, null, materialR.attr.materialButtonOutlinedStyle).apply {
			this.id = id
			setText(label)
			minHeight = context.dp(48)
		}

	private fun toggleParams() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
}
