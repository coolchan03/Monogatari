package org.koitharu.kotatsu.sync.profile

import android.content.Context
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R

/**
 * Asks which kinds of settings to apply from a backup or another device; secrets and per-device values never travel
 * (see [SettingsTransferProfile]). Network settings are often specific to one device (DNS, proxy), so they start
 * unticked. [onCancel] runs when the user backs out without applying anything.
 */
object SettingsGroupsDialog {

	fun show(
		context: Context,
		onCancel: (() -> Unit)? = null,
		onChosen: (Set<SettingsTransferProfile.Group>) -> Unit,
	) {
		val groups = SettingsTransferProfile.Group.entries
		val labels = groups.map { group ->
			context.getString(
				when (group) {
					SettingsTransferProfile.Group.APPEARANCE -> R.string.nearby_settings_appearance
					SettingsTransferProfile.Group.READER -> R.string.nearby_settings_reader
					SettingsTransferProfile.Group.LIBRARY -> R.string.nearby_settings_library
					SettingsTransferProfile.Group.SOURCES -> R.string.nearby_settings_sources
					SettingsTransferProfile.Group.NETWORK -> R.string.nearby_settings_network
					SettingsTransferProfile.Group.TRACKING -> R.string.nearby_settings_tracking
					SettingsTransferProfile.Group.OTHER -> R.string.nearby_settings_other
				},
			)
		}
		val checked = BooleanArray(groups.size) { groups[it] != SettingsTransferProfile.Group.NETWORK }
		val builder = MaterialAlertDialogBuilder(context)
			.setTitle(R.string.nearby_settings_groups_title)
			.setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, value -> checked[which] = value }
			.setCancelable(false)
			.setPositiveButton(R.string.nearby_settings_groups_apply) { _, _ ->
				onChosen(groups.filterIndexed { index, _ -> checked[index] }.toSet())
			}
		if (onCancel != null) {
			builder.setNegativeButton(android.R.string.cancel) { _, _ -> onCancel() }
		}
		builder.show()
	}
}
