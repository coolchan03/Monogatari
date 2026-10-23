package org.koitharu.kotatsu.core.ui

import android.app.Activity
import android.app.Application
import android.os.Build
import android.os.Bundle
import androidx.annotation.ColorInt
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import org.koitharu.kotatsu.R
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

	/** Android 11 and older cannot generate a scheme from a colour: any accent becomes black (light) / white (night). */
	fun apply(activity: Activity, settings: AppSettings) {
		val seed = settings.accentColor ?: return
		if (isSupported()) {
			DynamicColors.applyToActivityIfAvailable(activity, options(seed))
		} else {
			activity.theme.applyStyle(R.style.ThemeOverlay_Monogatari_AccentMono, true)
		}
	}
}

/**
 * Covers activities that don't extend BaseActivity (BaseActivity applies the accent itself, after its own theme
 * overlay, which would otherwise win).
 *
 * The Monogatari screens built on a plain AppCompatActivity (API keys, source log, WebDAV, readers, feeds, Test
 * Center...) also get the selected colour scheme and the AMOLED overlay here, in the same order as BaseActivity;
 * before, they always showed the default scheme, so they did not match the rest of the app.
 */
@Singleton
class AccentColorHelper @Inject constructor(
	private val settings: AppSettings,
) : DefaultActivityLifecycleCallbacks {

	override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (activity !is BaseActivity<*>) applyAll(activity)
	}

	/** Android 9 and older have no pre-created callback; this still runs before the screen builds its views. */
	override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && activity !is BaseActivity<*>) applyAll(activity)
	}

	private fun applyAll(activity: Activity) {
		if (activity.javaClass.name.startsWith(APP_PACKAGE)) {
			runCatching {
				activity.theme.applyStyle(settings.colorScheme.styleResId, true)
				if (settings.isAmoledTheme) {
					activity.theme.applyStyle(R.style.ThemeOverlay_Kotatsu_Amoled, true)
				}
			}
		}
		AccentColors.apply(activity, settings)
	}

	private companion object {
		const val APP_PACKAGE = "org.koitharu.kotatsu."
	}
}
