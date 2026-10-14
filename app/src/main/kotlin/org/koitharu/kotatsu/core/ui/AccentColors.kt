package org.koitharu.kotatsu.core.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.annotation.ColorInt
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import org.koitharu.kotatsu.core.prefs.AppSettings
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies the user's accent colour: Material builds a full light and dark colour scheme from the seed, so text
 * and controls keep their contrast in both modes. Requires Android 12+ (where Material's colour override is
 * available); on older versions the chosen colour theme stays in place.
 */
object AccentColors {

	fun isSupported(): Boolean = DynamicColors.isDynamicColorAvailable()

	fun options(@ColorInt seed: Int): DynamicColorsOptions =
		DynamicColorsOptions.Builder().setContentBasedSource(seed).build()

	fun apply(activity: Activity, settings: AppSettings) {
		val seed = settings.accentColor ?: return
		DynamicColors.applyToActivityIfAvailable(activity, options(seed))
	}
}

/**
 * Covers activities that don't extend BaseActivity (BaseActivity applies the accent itself, after its own theme
 * overlay, which would otherwise win).
 */
@Singleton
class AccentColorHelper @Inject constructor(
	private val settings: AppSettings,
) : DefaultActivityLifecycleCallbacks {

	override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (activity !is BaseActivity<*>) AccentColors.apply(activity, settings)
	}
}
