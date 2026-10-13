package org.koitharu.kotatsu.main.ui.welcome

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.viewModels
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.titleResId
import org.koitharu.kotatsu.core.nav.router
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.ui.widgets.ChipsView
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.core.util.ext.getDisplayName
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.tryLaunch
import org.koitharu.kotatsu.databinding.SheetWelcomeBinding
import org.koitharu.kotatsu.filter.ui.model.FilterProperty
import org.koitharu.kotatsu.parsers.model.ContentType
import org.koitharu.kotatsu.sync.domain.SyncController
import org.koitharu.kotatsu.sourcerepo.work.SourceBootstrapWorker
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class WelcomeSheet : BaseAdaptiveSheet<SheetWelcomeBinding>(), ChipsView.OnChipClickListener, View.OnClickListener,
	ActivityResultCallback<Uri?> {

	private val viewModel by viewModels<WelcomeViewModel>()

	@Inject
	lateinit var syncController: SyncController

    @Inject
    lateinit var bootstrapScheduler: SourceBootstrapWorker.Scheduler

	private val backupSelectCall = registerForActivityResult(
		ActivityResultContracts.OpenDocument(),
		this,
	)

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): SheetWelcomeBinding {
		return SheetWelcomeBinding.inflate(inflater, container, false)
	}

	override fun onViewBindingCreated(binding: SheetWelcomeBinding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		// Keep Monogatari's own welcome heading visible on tablets as well as phones.
		binding.buttonStartExploring.setOnClickListener { showAdultContentChoice() }
		binding.chipsLocales.onChipClickListener = this
		binding.chipsType.onChipClickListener = this
		binding.chipBackup.setOnClickListener(this)
		binding.chipSync.setOnClickListener(this)
		binding.chipDirectories.setOnClickListener(this)

		viewModel.locales.observe(viewLifecycleOwner, ::onLocalesChanged)
		viewModel.types.observe(viewLifecycleOwner, ::onTypesChanged)
	}

	private fun showAdultContentChoice() {
		AlertDialog.Builder(requireContext())
			.setTitle(R.string.welcome_adult_content_title)
			.setMessage(R.string.welcome_adult_content_description)
			.setNegativeButton(R.string.welcome_adult_content_no) { _, _ ->
                viewModel.setAdultContentEnabled(false)
                showInitialSourceSetupChoice(false)
			}
			.setPositiveButton(R.string.welcome_adult_content_yes) { _, _ ->
                viewModel.setAdultContentEnabled(true)
                showInitialSourceSetupChoice(true)
			}
			.setCancelable(false)
			.show()
	}

    private fun showInitialSourceSetupChoice(allowAdult: Boolean) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.welcome_source_setup_title)
            .setMessage(R.string.welcome_source_setup_message)
            .setPositiveButton(R.string.welcome_source_setup_yes) { _, _ ->
                bootstrapScheduler.start(allowAdult)
                dismiss()
            }
            .setNegativeButton(R.string.welcome_source_setup_skip) { _, _ -> dismiss() }
            .setCancelable(false)
            .show()
    }

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		viewBinding?.scrollView?.updatePadding(
			bottom = insets.getInsets(typeMask).bottom,
		)
		return insets.consume(v, typeMask, bottom = true)
	}

	override fun onChipClick(chip: Chip, data: Any?) {
		when (data) {
			is ContentType -> viewModel.setTypeChecked(data, !chip.isChecked)
			is Locale -> viewModel.setLocaleChecked(data, !chip.isChecked)
		}
	}

	override fun onClick(v: View) {
		when (v.id) {
			R.id.chip_backup -> {
				if (!backupSelectCall.tryLaunch(arrayOf("*/*"))) {
					Snackbar.make(
						v, R.string.operation_not_supported, Snackbar.LENGTH_SHORT,
					).show()
				}
			}

			R.id.chip_sync -> {
				syncController.addAccount(requireActivity()) {}
			}

            R.id.chip_directories -> {
                router.openDirectoriesSettings()
            }
		}
	}

	override fun onActivityResult(result: Uri?) {
		if (result != null) {
			router.showBackupRestoreDialog(result)
		}
	}

	private fun onLocalesChanged(value: FilterProperty<Locale>) {
		val chips = viewBinding?.chipsLocales ?: return
		chips.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = it.getDisplayName(chips.context),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}

	private fun onTypesChanged(value: FilterProperty<ContentType>) {
		val chips = viewBinding?.chipsType ?: return
		chips.setChips(
			value.availableItems.map {
				ChipsView.ChipModel(
					title = getString(it.titleResId),
					isChecked = it in value.selectedItems,
					data = it,
				)
			},
		)
	}
}
