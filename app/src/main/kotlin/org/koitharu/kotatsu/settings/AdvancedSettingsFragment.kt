package org.koitharu.kotatsu.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.qa.MonogatariTestCenterActivity
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment
import org.koitharu.kotatsu.main.ui.welcome.SetupSheet

/** Rarely used and technical options: storage and network, services and testing tools. */
@AndroidEntryPoint
class AdvancedSettingsFragment : BasePreferenceFragment(R.string.monogatari_advanced_title) {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_advanced)
        findPreference<Preference>("monogatari_qa_entry")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), MonogatariTestCenterActivity::class.java))
            true
        }
        findPreference<Preference>("monogatari_run_setup_again")?.setOnPreferenceClickListener {
            SetupSheet().show(childFragmentManager, SetupSheet.TAG)
            true
        }
        for ((key, target) in SettingsGroupFragment.ACTIVITY_LINKS) {
            findPreference<Preference>(key)?.setOnPreferenceClickListener {
                startActivity(Intent(requireContext(), target))
                true
            }
        }
    }
}
