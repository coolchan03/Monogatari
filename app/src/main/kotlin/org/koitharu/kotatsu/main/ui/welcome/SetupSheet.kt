package org.koitharu.kotatsu.main.ui.welcome

import android.Manifest
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.preference.PreferenceManager
import androidx.viewbinding.ViewBinding
import com.google.android.material.progressindicator.LinearProgressIndicator
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.addBodyText
import org.koitharu.kotatsu.core.ui.addHeaderCard
import org.koitharu.kotatsu.core.ui.addSectionCard
import org.koitharu.kotatsu.core.ui.buttonParams
import org.koitharu.kotatsu.core.ui.dp
import org.koitharu.kotatsu.core.ui.primaryButton
import org.koitharu.kotatsu.core.ui.secondaryButton
import org.koitharu.kotatsu.core.ui.sheet.BaseAdaptiveSheet
import org.koitharu.kotatsu.core.ui.statusText
import org.koitharu.kotatsu.core.ui.util.ActivityRecreationHandle
import org.koitharu.kotatsu.core.util.ext.checkNotificationPermission
import org.koitharu.kotatsu.core.util.ext.consume
import org.koitharu.kotatsu.core.util.ext.getThemeColor
import org.koitharu.kotatsu.core.util.ext.setThemeTextAppearance
import org.koitharu.kotatsu.settings.SettingsActivity
import javax.inject.Inject
import com.google.android.material.R as materialR

/**
 * "Set up Monogatari": theme, sources, notifications and a map of the app. Shown once after the first-run welcome
 * sheet (or from Settings, Advanced, Run setup again). Skip, Done, swiping it away or pressing back all finish it.
 */
@AndroidEntryPoint
class SetupSheet : BaseAdaptiveSheet<SetupSheet.Binding>() {

	@Inject
	lateinit var settings: AppSettings

	@Inject
	lateinit var activityRecreationHandle: ActivityRecreationHandle

	private val steps = SetupFlow.steps(Build.VERSION.SDK_INT)
	private var index = 0
	private var scroll: ScrollView? = null
	private lateinit var progress: LinearProgressIndicator
	private lateinit var progressLabel: TextView
	private lateinit var content: LinearLayout
	private lateinit var backButton: View
	private lateinit var nextButton: com.google.android.material.button.MaterialButton

	private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
		render()
	}

	class Binding(private val view: View) : ViewBinding {
		override fun getRoot(): View = view
	}

	override fun onCreateViewBinding(inflater: LayoutInflater, container: ViewGroup?): Binding {
		index = 0
		val context = inflater.context
		val column = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			val pad = dp(16)
			setPadding(pad, pad, pad, pad)
		}
		column.addView(TextView(context).apply {
			setText(R.string.first_run_setup_title)
			setThemeTextAppearance(materialR.attr.textAppearanceTitleLarge, materialR.style.TextAppearance_Material3_TitleLarge)
		})
		progress = LinearProgressIndicator(context).apply {
			max = steps.size
		}
		column.addView(progress, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12) })
		progressLabel = context.statusText().apply { setPadding(0, dp(4), 0, dp(12)) }
		column.addView(progressLabel)
		content = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
		column.addView(content, LinearLayout.LayoutParams(-1, -2))

		val nav = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
		}
		backButton = context.secondaryButton(getString(R.string.first_run_back)) {
			index = SetupFlow.previous(index)
			render()
		}
		val skip = context.secondaryButton(getString(R.string.first_run_skip)) { dismissAllowingStateLoss() }
		nextButton = context.primaryButton(getString(R.string.first_run_next)) {
			if (index >= steps.lastIndex) dismissAllowingStateLoss() else {
				index = SetupFlow.next(index, steps.size)
				render()
			}
		}
		for (button in listOf(backButton, skip, nextButton)) {
			nav.addView(button, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dp(8) })
		}
		column.addView(nav, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(16) })

		val scrollView = ScrollView(context).apply {
			isFillViewport = true
			addView(column, ViewGroup.LayoutParams(-1, -2))
		}
		scroll = scrollView
		return Binding(scrollView)
	}

	override fun onViewBindingCreated(binding: Binding, savedInstanceState: Bundle?) {
		super.onViewBindingCreated(binding, savedInstanceState)
		index = (savedInstanceState?.getInt(KEY_INDEX) ?: 0).coerceIn(0, steps.lastIndex)
		render()
	}

	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putInt(KEY_INDEX, index)
	}

	override fun onDestroyView() {
		scroll = null
		super.onDestroyView()
	}

	override fun onDismiss(dialog: DialogInterface) {
		// However the sheet closes, the user has seen it: never show it again on its own.
		context?.let(SetupFlow::markDone)
		super.onDismiss(dialog)
	}

	override fun onApplyWindowInsets(v: View, insets: WindowInsetsCompat): WindowInsetsCompat {
		val typeMask = WindowInsetsCompat.Type.systemBars()
		scroll?.updatePadding(bottom = insets.getInsets(typeMask).bottom)
		return insets.consume(v, typeMask, bottom = true)
	}

	private fun render() {
		val context = context ?: return
		progress.setProgressCompat(index + 1, true)
		progressLabel.text = getString(R.string.first_run_step_progress, index + 1, steps.size)
		backButton.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
		nextButton.setText(if (index >= steps.lastIndex) R.string.first_run_finish else R.string.first_run_next)
		content.removeAllViews()
		when (steps[index]) {
			SetupFlow.Step.THEME -> renderTheme(context)
			SetupFlow.Step.SOURCES -> renderSources(context)
			SetupFlow.Step.NOTIFICATIONS -> renderNotifications(context)
			SetupFlow.Step.WHERE -> renderWhere(context)
		}
	}

	private fun renderTheme(context: Context) {
		content.addHeaderCard(
			MascotForm.DEFAULT,
			getString(R.string.first_run_theme_title),
			getString(R.string.first_run_theme_message),
		)
		val current = currentTheme()
		for (choice in ThemeChoice.entries) {
			val label = getString(choice.label)
			val button = if (choice == current) context.primaryButton(label) { applyTheme(choice) }
			else context.secondaryButton(label) { applyTheme(choice) }
			content.addView(button, context.buttonParams())
		}
	}

	private fun currentTheme(): ThemeChoice = when (settings.theme) {
		AppCompatDelegate.MODE_NIGHT_NO -> ThemeChoice.LIGHT
		AppCompatDelegate.MODE_NIGHT_YES -> if (settings.isAmoledTheme) ThemeChoice.AMOLED else ThemeChoice.DARK
		else -> ThemeChoice.SYSTEM
	}

	/** Writes the same preference keys as Settings, Appearance, then applies them straight away. */
	private fun applyTheme(choice: ThemeChoice) {
		val context = context ?: return
		if (choice == currentTheme()) return
		val amoledChanged = choice.amoled != null && choice.amoled != settings.isAmoledTheme
		PreferenceManager.getDefaultSharedPreferences(context).edit {
			putString(AppSettings.KEY_THEME, choice.mode.toString())
			choice.amoled?.let { putBoolean(AppSettings.KEY_THEME_AMOLED, it) }
		}
		if (AppCompatDelegate.getDefaultNightMode() != settings.theme) {
			// Switching the night mode recreates the screens by itself.
			AppCompatDelegate.setDefaultNightMode(settings.theme)
		} else if (amoledChanged) {
			activityRecreationHandle.recreateAll()
		}
		render()
	}

	private fun renderSources(context: Context) {
		val body = content.addHeaderCard(
			MascotForm.MANGA,
			getString(R.string.first_run_sources_title),
			getString(R.string.first_run_sources_message),
		)
		body.setPadding(0, dp(4), 0, 0)
		content.addView(context.primaryButton(getString(R.string.first_run_sources_action)) {
			val intent = Intent(context, SettingsActivity::class.java)
				.setAction(Intent.ACTION_VIEW)
				.setData(Uri.parse("monogatari://source-repos"))
			startActivity(intent)
		}, context.buttonParams())
	}

	private fun renderNotifications(context: Context) {
		content.addHeaderCard(
			MascotForm.NEWS,
			getString(R.string.first_run_notifications_title),
			getString(R.string.first_run_notifications_message),
		)
		if (context.checkNotificationPermission(null)) {
			content.addBodyText(getString(R.string.first_run_notifications_granted))
		} else {
			content.addView(context.primaryButton(getString(R.string.first_run_notifications_action)) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
					notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
				}
			}, context.buttonParams())
		}
	}

	private fun renderWhere(context: Context) {
		content.addHeaderCard(
			MascotForm.BOOK,
			getString(R.string.first_run_where_title),
			getString(R.string.first_run_where_message),
		)
		val card = content.addSectionCard(null)
		addPlace(card, R.drawable.ic_grid, R.string.first_run_where_library, R.string.first_run_where_library_message)
		addPlace(card, R.drawable.ic_star_rate_outline, R.string.first_run_where_discover, R.string.first_run_where_discover_message)
		addPlace(card, R.drawable.ic_monogatari_nav_explore, R.string.first_run_where_search, R.string.first_run_where_search_message)
		addPlace(card, R.drawable.ic_settings, R.string.first_run_where_settings, R.string.first_run_where_settings_message)
	}

	private fun addPlace(
		parent: LinearLayout,
		@DrawableRes icon: Int,
		@StringRes title: Int,
		@StringRes message: Int,
	) {
		val context = parent.context
		val row = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			setPadding(0, dp(8), 0, dp(8))
		}
		row.addView(ImageView(context).apply {
			setImageResource(icon)
			imageTintList = ColorStateList.valueOf(context.getThemeColor(materialR.attr.colorOnSurfaceVariant))
			importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
		}, LinearLayout.LayoutParams(dp(24), dp(24)).apply { marginEnd = dp(16) })
		val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
		texts.addView(TextView(context).apply {
			setText(title)
			setThemeTextAppearance(materialR.attr.textAppearanceTitleSmall, materialR.style.TextAppearance_Material3_TitleSmall)
		})
		texts.addView(TextView(context).apply {
			setText(message)
			setThemeTextAppearance(materialR.attr.textAppearanceBodyMedium, materialR.style.TextAppearance_Material3_BodyMedium)
		})
		row.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
		parent.addView(row, LinearLayout.LayoutParams(-1, -2))
	}

	private fun dp(value: Int): Int = requireContext().dp(value)

	/** Theme options, as stored by Settings, Appearance: night mode (-1 system, 1 light, 2 dark) plus the black switch. */
	private enum class ThemeChoice(@StringRes val label: Int, val mode: Int, val amoled: Boolean?) {
		SYSTEM(R.string.first_run_theme_system, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, null),
		LIGHT(R.string.first_run_theme_light, AppCompatDelegate.MODE_NIGHT_NO, null),
		DARK(R.string.first_run_theme_dark, AppCompatDelegate.MODE_NIGHT_YES, false),
		AMOLED(R.string.first_run_theme_amoled, AppCompatDelegate.MODE_NIGHT_YES, true),
	}

	companion object {
		const val TAG = "SetupSheet"
		private const val KEY_INDEX = "setup_step"
	}
}
