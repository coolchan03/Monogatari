package org.koitharu.kotatsu.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.annotation.StringRes
import androidx.annotation.XmlRes
import androidx.fragment.app.viewModels
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.backups.domain.ForeignBackupReviewActivity
import org.koitharu.kotatsu.backups.domain.NearbyOfflineSyncActivity
import org.koitharu.kotatsu.backups.domain.WebDavManualSyncActivity
import org.koitharu.kotatsu.bookmarks.ui.AllBookmarksActivity
import org.koitharu.kotatsu.core.keys.ApiKeysActivity
import org.koitharu.kotatsu.core.media.BookLibraryActivity
import org.koitharu.kotatsu.core.media.MediaFeedActivity
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.qa.NewsFeedActivity
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.util.ext.getQuantityStringSafe
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.discover.DiscoverActivity
import org.koitharu.kotatsu.download.ui.list.DownloadsActivity
import org.koitharu.kotatsu.main.ui.OpenSourceLicensesActivity
import org.koitharu.kotatsu.sourcehealth.SourceLogActivity
import org.koitharu.kotatsu.stats.ui.StatsActivity

/**
 * A page of the settings root's groups. It only links to existing settings screens (android:fragment, optionally
 * with a pref_key extra that highlights one entry there) and to Monogatari pages that are not preference screens;
 * no preference is stored here, so every key keeps living on its original screen.
 */
abstract class SettingsGroupFragment(
	@StringRes titleId: Int,
	@XmlRes private val xmlRes: Int,
) : BasePreferenceFragment(titleId) {

	override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
		addPreferencesFromResource(xmlRes)
		for ((key, target) in ACTIVITY_LINKS) {
			findPreference<Preference>(key)?.setOnPreferenceClickListener {
				startActivity(Intent(requireContext(), target))
				true
			}
		}
	}

	companion object {

		/** Entries that open a page of their own rather than a settings screen. Keys are not persisted. */
		val ACTIVITY_LINKS: Map<String, Class<out Activity>> = mapOf(
			"monogatari_books_entry" to BookLibraryActivity::class.java,
			"monogatari_link_downloads_list" to DownloadsActivity::class.java,
			"monogatari_link_bookmarks" to AllBookmarksActivity::class.java,
			"monogatari_link_stats" to StatsActivity::class.java,
			"monogatari_link_api_keys" to ApiKeysActivity::class.java,
			"monogatari_link_source_log" to SourceLogActivity::class.java,
			"monogatari_link_discover" to DiscoverActivity::class.java,
			"monogatari_link_news" to NewsFeedActivity::class.java,
			"monogatari_link_podcasts" to MediaFeedActivity::class.java,
			"monogatari_link_nearby" to NearbyOfflineSyncActivity::class.java,
			"monogatari_link_webdav" to WebDavManualSyncActivity::class.java,
			"monogatari_link_foreign_import" to ForeignBackupReviewActivity::class.java,
			"monogatari_link_licences" to OpenSourceLicensesActivity::class.java,
		)
	}
}

@AndroidEntryPoint
class LibrarySettingsGroupFragment :
	SettingsGroupFragment(R.string.settings_group_library, R.xml.pref_group_library)

@AndroidEntryPoint
class SourcesSettingsGroupFragment :
	SettingsGroupFragment(R.string.settings_group_sources, R.xml.pref_group_sources) {

	private val viewModel: RootSettingsViewModel by viewModels()

	override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
		super.onViewCreated(view, savedInstanceState)
		findPreference<Preference>(AppSettings.KEY_REMOTE_SOURCES)?.let { pref ->
			val total = viewModel.totalSourcesCount
			viewModel.enabledSourcesCount.observe(viewLifecycleOwner) {
				pref.summary = if (it >= 0) {
					getString(R.string.enabled_d_of_d, it, total)
				} else {
					resources.getQuantityStringSafe(R.plurals.items, total, total)
				}
			}
		}
	}
}

@AndroidEntryPoint
class DiscoverSettingsGroupFragment :
	SettingsGroupFragment(R.string.settings_group_discover, R.xml.pref_group_discover)

@AndroidEntryPoint
class NotificationsSettingsGroupFragment :
	SettingsGroupFragment(R.string.settings_group_notifications, R.xml.pref_group_notifications)

@AndroidEntryPoint
class BackupSettingsGroupFragment :
	SettingsGroupFragment(R.string.settings_group_backup, R.xml.pref_group_backup)

@AndroidEntryPoint
class PrivacySettingsGroupFragment :
	SettingsGroupFragment(R.string.settings_group_privacy, R.xml.pref_group_privacy)
