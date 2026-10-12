package org.koitharu.kotatsu.settings

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.qa.MonogatariTestCenterActivity
import org.koitharu.kotatsu.core.ui.BasePreferenceFragment

/** Testing tools live under Advanced; normal reading and setup have no diagnostic prompts. */
@AndroidEntryPoint
class AdvancedSettingsFragment : BasePreferenceFragment(R.string.monogatari_advanced_title) {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.pref_advanced)
        findPreference<Preference>("monogatari_qa_entry")?.setOnPreferenceClickListener {
            startActivity(Intent(requireContext(), MonogatariTestCenterActivity::class.java))
            true
        }
    }
}
