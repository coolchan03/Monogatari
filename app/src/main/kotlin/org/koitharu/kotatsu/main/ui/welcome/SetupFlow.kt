package org.koitharu.kotatsu.main.ui.welcome

import android.content.Context
import androidx.core.content.edit

/** Rules and storage for the short "Set up Monogatari" flow shown once after the welcome sheet. */
object SetupFlow {

	const val PREFS = "monogatari_first_run"
	const val KEY_DONE = "setup_done"

	/** POST_NOTIFICATIONS is a runtime permission from Android 13 (API 33). */
	private const val NOTIFICATION_PERMISSION_SDK = 33

	enum class Step { THEME, SOURCES, NOTIFICATIONS, WHERE }

	/** The steps for a device running [sdkInt]; the notifications step is skipped below Android 13. */
	fun steps(sdkInt: Int): List<Step> = Step.entries.filter {
		it != Step.NOTIFICATIONS || sdkInt >= NOTIFICATION_PERMISSION_SDK
	}

	/**
	 * The setup flow follows the welcome sheet, which only a fresh install shows, so existing users never see it:
	 * show it when the welcome has just been completed and setup was not finished or skipped before.
	 */
	fun shouldShow(setupDone: Boolean, welcomeJustCompleted: Boolean): Boolean = welcomeJustCompleted && !setupDone

	/** Next step index, clamped to the last step. */
	fun next(index: Int, count: Int): Int = (index + 1).coerceAtMost(count - 1)

	/** Previous step index, clamped to the first step. */
	fun previous(index: Int): Int = (index - 1).coerceAtLeast(0)

	fun isDone(context: Context): Boolean = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
		.getBoolean(KEY_DONE, false)

	fun markDone(context: Context) {
		context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_DONE, true) }
	}
}
