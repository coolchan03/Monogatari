package org.koitharu.kotatsu.settings

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import androidx.fragment.app.viewModels
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import com.google.android.material.snackbar.Snackbar
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.exceptions.resolve.SnackbarErrorObserver
import org.koitharu.kotatsu.core.network.DoHProvider
import org.koitharu.kotatsu.core.media.BookLibraryActivity
import org.koitharu.kotatsu.core.prefs.AppSettings
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.core.ui.dialog.buildAlertDialog
import org.koitharu.kotatsu.core.util.ext.observe
import org.koitharu.kotatsu.core.util.ext.observeEvent
import org.koitharu.kotatsu.core.util.ext.setDefaultValueCompat
import org.koitharu.kotatsu.parsers.util.names
import org.koitharu.kotatsu.settings.userdata.storage.StorageUsagePreference
import org.koitharu.kotatsu.storage.StorageAction
import org.koitharu.kotatsu.storage.StorageBreakdown
import org.koitharu.kotatsu.storage.StorageEntry
import org.koitharu.kotatsu.storage.StorageOverview
import java.net.Proxy

class StorageAndNetworkSettingsFragment :
    BasePreferenceFragment(R.string.storage_and_network),
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val viewModel by viewModels<StorageAndNetworkSettingsViewModel>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_network_storage)
        findPreference<ListPreference>(AppSettings.KEY_DOH)?.run {
            entryValues = DoHProvider.entries.names()
            setDefaultValueCompat(DoHProvider.NONE.name)
        }
        bindProxySummary()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel.onError.observeEvent(viewLifecycleOwner, SnackbarErrorObserver(listView, this))
        settings.subscribe(this)
        findPreference<StorageUsagePreference>(AppSettings.KEY_STORAGE_USAGE)?.let { pref ->
            viewModel.storageUsage.observe(viewLifecycleOwner, pref)
        }
        viewModel.overview.observe(viewLifecycleOwner) { bindOverview(it) }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshOverview()
    }

    override fun onDestroyView() {
        settings.unsubscribe(this)
        super.onDestroyView()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        when (key) {
            AppSettings.KEY_SSL_BYPASS -> {
                Snackbar.make(listView, R.string.settings_apply_restart_required, Snackbar.LENGTH_INDEFINITE).show()
            }

            AppSettings.KEY_PROXY_TYPE,
            AppSettings.KEY_PROXY_ADDRESS,
            AppSettings.KEY_PROXY_PORT -> {
                bindProxySummary()
            }
        }
    }

    private fun bindProxySummary() {
        findPreference<Preference>(AppSettings.KEY_PROXY)?.run {
            val type = settings.proxyType
            val address = settings.proxyAddress
            val port = settings.proxyPort
            summary = when {
                type == Proxy.Type.DIRECT -> context.getString(R.string.disabled)
                address.isNullOrEmpty() || port == 0 -> context.getString(R.string.invalid_proxy_configuration)
                else -> "$address:$port"
            }
        }
    }

    private fun formatSize(bytes: Long): String {
        val units = getString(R.string.text_file_sizes).split('|')
        return StorageBreakdown.formatBytes(bytes, units)
    }

    private fun titleOf(entry: StorageEntry): String = entry.title ?: getString(
        when (entry.id) {
            StorageEntry.SAVED_MANGA -> R.string.storage_overview_saved_manga
            StorageEntry.BOOKS -> R.string.storage_overview_books
            StorageEntry.PAGES_CACHE -> R.string.storage_overview_pages_cache
            StorageEntry.OTHER_CACHE -> R.string.storage_overview_other_cache
            StorageEntry.AUTO_DOWNLOADS -> R.string.storage_overview_auto_downloads
            StorageEntry.FEED_CACHES -> R.string.storage_overview_feed_caches
            StorageEntry.EXTENSION_CODE -> R.string.storage_overview_extension_code
            else -> R.string.other_cache
        },
    )

    private fun bindOverview(overview: StorageOverview?) {
        val header = findPreference<Preference>(KEY_OVERVIEW_HEADER)
        val hint = findPreference<Preference>(KEY_LOW_HINT)
        val category = findPreference<PreferenceCategory>(KEY_BREAKDOWN)
        if (overview == null) {
            header?.summary = getString(R.string.computing_)
            hint?.isVisible = false
            return
        }
        header?.summary = if (overview.totalBytes > 0L && overview.freeBytes >= 0L) {
            getString(
                R.string.storage_overview_header_summary,
                formatSize(overview.freeBytes),
                formatSize(overview.totalBytes),
                formatSize(overview.appBytes),
                StorageBreakdown.sharePercent(overview.appBytes, overview.totalBytes),
            )
        } else {
            getString(
                R.string.storage_overview_header_summary_unknown_total,
                formatSize(overview.freeBytes.coerceAtLeast(0L)),
                formatSize(overview.appBytes),
            )
        }
        val biggest = overview.biggestClearable
        hint?.run {
            isVisible = overview.isLowStorage
            if (overview.isLowStorage) {
                summary = if (biggest != null) {
                    getString(
                        R.string.storage_overview_low_summary_clear,
                        formatSize(overview.freeBytes),
                        formatSize(biggest.bytes),
                        titleOf(biggest),
                    )
                } else {
                    getString(R.string.storage_overview_low_summary_manual, formatSize(overview.freeBytes))
                }
                setOnPreferenceClickListener {
                    if (biggest != null) {
                        onEntryClick(biggest)
                    }
                    true
                }
            }
        }
        category ?: return
        // Drop the rows of the previous pass, keep the header and the low-storage hint.
        for (i in category.preferenceCount - 1 downTo 0) {
            val child = category.getPreference(i)
            if (child.key?.startsWith(KEY_ENTRY_PREFIX) == true) {
                category.removePreference(child)
            }
        }
        for (entry in overview.entries) {
            val pref = Preference(requireContext())
            pref.key = KEY_ENTRY_PREFIX + entry.id
            pref.isPersistent = false
            pref.isSingleLineTitle = false
            pref.title = titleOf(entry)
            val size = formatSize(entry.bytes)
            pref.summary = if (entry.action == StorageAction.OPEN_LIBRARY) {
                getString(R.string.storage_overview_books_summary, size)
            } else {
                getString(
                    R.string.storage_overview_entry_summary,
                    size,
                    StorageBreakdown.sharePercent(entry.bytes, overview.appBytes),
                )
            }
            pref.isSelectable = entry.action != StorageAction.NONE
            pref.setOnPreferenceClickListener {
                onEntryClick(entry)
                true
            }
            category.addPreference(pref)
        }
    }

    private fun onEntryClick(entry: StorageEntry) {
        val context = context ?: return
        when (entry.action) {
            StorageAction.NONE -> Unit
            StorageAction.OPEN_LIBRARY -> startActivity(Intent(context, BookLibraryActivity::class.java))
            else -> confirmAction(entry)
        }
    }

    private fun confirmAction(entry: StorageEntry) {
        val context = context ?: return
        val message = when (entry.id) {
            StorageEntry.SAVED_MANGA -> getString(R.string.storage_overview_saved_manga_confirm)
            StorageEntry.AUTO_DOWNLOADS -> getString(R.string.storage_overview_auto_downloads_confirm)
            StorageEntry.FEED_CACHES -> getString(R.string.storage_overview_feed_caches_confirm)
            else -> getString(R.string.storage_overview_confirm, formatSize(entry.bytes))
        }
        val positive = when (entry.action) {
            StorageAction.REMOVE_AUTO_DOWNLOADS -> R.string.storage_overview_auto_downloads_remove
            StorageAction.MANAGE -> R.string.delete
            else -> R.string.clear
        }
        buildAlertDialog(context) {
            setTitle(titleOf(entry))
            setMessage(message)
            setNegativeButton(android.R.string.cancel, null)
            setPositiveButton(positive) { _, _ ->
                viewModel.perform(entry)
            }
        }.show()
    }
}

private const val KEY_BREAKDOWN = "storage_breakdown"

private const val KEY_OVERVIEW_HEADER = "storage_overview_header"

private const val KEY_LOW_HINT = "storage_low_hint"

private const val KEY_ENTRY_PREFIX = "storage_entry_"
