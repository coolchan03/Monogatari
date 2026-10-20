package org.koitharu.kotatsu.main.ui

import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.ui.MascotForm
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.addBodyText
import org.koitharu.kotatsu.core.ui.addHeaderCard
import org.koitharu.kotatsu.core.ui.addPrimaryButton
import org.koitharu.kotatsu.core.ui.addSecondaryButton
import org.koitharu.kotatsu.core.ui.addSectionCard
import org.koitharu.kotatsu.core.ui.pageColumn
import org.koitharu.kotatsu.core.ui.scrollable
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Offline-readable project and upstream credits, linked to full source notices. */
class OpenSourceLicensesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = getString(R.string.screen_title_licences)
        val root = pageColumn()
        root.addHeaderCard(
            MascotForm.BOOK,
            getString(R.string.mono_screen_header_licences),
            getString(R.string.mono_screen_header_licences_body),
        )
        val text = assets.open("monogatari_open_source_notices.txt").bufferedReader()
            .use { it.readText() }
        root.addSectionCard(getString(R.string.mono_section_licences_notices)).addBodyText(text).apply {
            setTextIsSelectable(true)
        }
        val actions = listOf(
            "Full project GPL-3.0 licence" to "https://github.com/coolchan03/Monogatari/blob/HEAD/LICENSE",
            "Third-party source notices" to "https://github.com/coolchan03/Monogatari/blob/HEAD/NOTICE",
            "Monogatari source code" to "https://github.com/coolchan03/Monogatari",
        )
        val links = root.addSectionCard(getString(R.string.mono_section_licences_links))
        for ((index, action) in actions.withIndex()) {
            val (label, url) = action
            val openLink = {
                runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    .onFailure {
                        android.widget.Toast.makeText(
                            this@OpenSourceLicensesActivity, "No browser available",
                            android.widget.Toast.LENGTH_SHORT,
                        ).show()
                    }
                Unit
            }
            // The licence itself is the main link; the others are outlined.
            if (index == 0) links.addPrimaryButton(label, openLink) else links.addSecondaryButton(label, openLink)
        }
        setMonogatariContent(root.scrollable(), MonogatariChrome.TEXT_MAX_WIDTH_DP)
    }
}
