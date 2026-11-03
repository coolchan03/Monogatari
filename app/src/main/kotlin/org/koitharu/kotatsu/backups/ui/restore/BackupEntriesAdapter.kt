package org.koitharu.kotatsu.backups.ui.restore

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import com.hannesdorfmann.adapterdelegates4.dsl.adapterDelegateViewBinding
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.BaseListAdapter
import org.koitharu.kotatsu.core.ui.list.OnListItemClickListener
import org.koitharu.kotatsu.core.util.ext.setChecked
import org.koitharu.kotatsu.databinding.ItemCheckableMultipleBinding
import org.koitharu.kotatsu.list.ui.ListModelDiffCallback.Companion.PAYLOAD_CHECKED_CHANGED
import org.koitharu.kotatsu.list.ui.adapter.ListItemType

class BackupSectionsAdapter(
	clickListener: OnListItemClickListener<BackupSectionModel>,
) : BaseListAdapter<BackupSectionModel>() {

	init {
		addDelegate(ListItemType.NAV_ITEM, backupSectionAD(clickListener))
	}
}

private fun backupSectionAD(
	clickListener: OnListItemClickListener<BackupSectionModel>,
) = adapterDelegateViewBinding<BackupSectionModel, BackupSectionModel, ItemCheckableMultipleBinding>(
	{ layoutInflater, parent -> ItemCheckableMultipleBinding.inflate(layoutInflater, parent, false) },
) {

	binding.root.setOnClickListener { v ->
		clickListener.onItemClick(item, v)
	}

	bind { payloads ->
		with(binding.root) {
			val preview = when {
				item.isPreviewLoading -> context.getString(R.string.restore_preview_checking)
				else -> item.preview?.let { RestorePreviewText.format(context, item.section, it) }
			}
			if (preview == null) {
				setText(item.titleResId)
			} else {
				text = SpannableStringBuilder(context.getString(item.titleResId))
					.append('\n')
					.append(preview, RelativeSizeSpan(0.85f), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
			}
			setChecked(item.isChecked, PAYLOAD_CHECKED_CHANGED in payloads)
			isEnabled = item.isEnabled
		}
	}
}
