package org.koitharu.kotatsu.settings

import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.activityViewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.BuildConfig
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.ColorScheme
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.addMenuProvider
import org.koitharu.kotatsu.settings.search.SettingsSearchMenuProvider
import org.koitharu.kotatsu.settings.search.SettingsSearchViewModel

@AndroidEntryPoint
class RootSettingsFragment : BasePreferenceFragment(0) {

	private val activityViewModel: SettingsSearchViewModel by activityViewModels()

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(R.xml.pref_root)
		addPreferencesFromResource(R.xml.pref_root_debug)
		findPreference<Preference>("about")?.summary =
			getString(R.string.settings_group_about_summary, BuildConfig.VERSION_NAME)
		applyExpressiveIconBackgrounds()
	}

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		addMenuProvider(SettingsSearchMenuProvider(activityViewModel))
	}

	override fun setTitle(title: CharSequence?) {
		if (!resources.getBoolean(R.bool.is_tablet)) {
			super.setTitle(title)
		}
	}

	private fun applyExpressiveIconBackgrounds() {
		if (settings.colorScheme != ColorScheme.EXPRESSIVE) {
			return
		}
		val context = requireContext()
		val iconColors = arrayOf(
			"appearance" to R.color.settings_icon_appearance,
			"reader" to R.color.settings_icon_reader,
			"monogatari_group_library" to R.color.settings_icon_downloads,
			"monogatari_group_sources" to R.color.settings_icon_sources,
			"monogatari_group_discover" to R.color.settings_icon_network,
			"monogatari_group_notifications" to R.color.settings_icon_tracker,
			"monogatari_group_backup" to R.color.settings_icon_backup,
			"monogatari_group_privacy" to R.color.settings_icon_appearance,
			"monogatari_advanced_entry" to R.color.settings_icon_services,
			"about" to R.color.settings_icon_about,
		)
		val density = resources.displayMetrics.density
		val size = (40f * density).toInt()
		val iconInset = (8f * density).toInt()
		val foregroundColor = ContextCompat.getColor(context, android.R.color.white)

		iconColors.forEach { (key, backgroundColorRes) ->
			val preference = findPreference<Preference>(key) ?: return@forEach
			val icon = preference.icon ?: return@forEach
			val background = GradientDrawable().apply {
				shape = GradientDrawable.OVAL
				setColor(ContextCompat.getColor(context, backgroundColorRes))
				setSize(size, size)
			}
			val foreground = DrawableCompat.wrap(icon.mutate()).also {
				DrawableCompat.setTint(it, foregroundColor)
			}
			preference.icon = LayerDrawable(arrayOf(background, foreground)).apply {
				setLayerInset(1, iconInset, iconInset, iconInset, iconInset)
			}
		}
	}
}
