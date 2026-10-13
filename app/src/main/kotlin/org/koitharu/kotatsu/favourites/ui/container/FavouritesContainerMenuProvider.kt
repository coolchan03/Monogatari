package org.koitharu.kotatsu.favourites.ui.container

import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.MenuProvider
import androidx.fragment.app.Fragment
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.nav.AppRouter

class FavouritesContainerMenuProvider(
	private val router: AppRouter,
	private val fragment: Fragment,
	private val viewModel: FavouritesContainerViewModel,
) : MenuProvider {

	override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
		menuInflater.inflate(R.menu.opt_favourites_container, menu)
	}

	override fun onPrepareMenu(menu: Menu) {
		super.onPrepareMenu(menu)
		val state = viewModel.privacyState.value
		menu.findItem(R.id.action_unlock_private)?.isVisible = state.hasPrivate && !state.isUnlocked
		menu.findItem(R.id.action_lock_private)?.isVisible = state.hasPrivate && state.isUnlocked
	}

	override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
		when (menuItem.itemId) {
			R.id.action_manage -> {
				router.openFavoriteCategories()
			}

			R.id.action_unlock_private -> authenticateAndUnlock()
			R.id.action_lock_private -> viewModel.lockPrivate()

			else -> return false
		}
		return true
	}

	private fun authenticateAndUnlock() {
		val context = fragment.context ?: return
		val authenticators = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
		if (BiometricManager.from(context).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
			// No screen lock on this device: nothing to check against, so just show them.
			Toast.makeText(context, R.string.private_categories_no_lock, Toast.LENGTH_LONG).show()
			viewModel.unlockPrivate()
			return
		}
		val prompt = BiometricPrompt(
			fragment,
			ContextCompat.getMainExecutor(context),
			object : BiometricPrompt.AuthenticationCallback() {
				override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
					viewModel.unlockPrivate()
				}
			},
		)
		val info = BiometricPrompt.PromptInfo.Builder()
			.setTitle(context.getString(R.string.private_categories_show))
			.setAllowedAuthenticators(authenticators)
			.build()
		prompt.authenticate(info)
	}
}
