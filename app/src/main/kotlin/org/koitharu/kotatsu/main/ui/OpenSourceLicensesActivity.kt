package org.koitharu.kotatsu.main.ui

import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton

/** Offline-readable project and upstream credits, linked to full source notices. */
class OpenSourceLicensesActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Open-source licences"
        val padding = (18 * resources.displayMetrics.density).toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val text = assets.open("monogatari_open_source_notices.txt").bufferedReader()
            .use { it.readText() }
        root.addView(TextView(this).apply {
            this.text = text
            textSize = 15f
            setTextIsSelectable(true)
        })
        val actions = listOf(
            "Full project GPL-3.0 licence" to "https://github.com/coolchan03/Monogatari/blob/HEAD/LICENSE",
            "Third-party source notices" to "https://github.com/coolchan03/Monogatari/blob/HEAD/NOTICE",
            "Monogatari source code" to "https://github.com/coolchan03/Monogatari",
        )
        for ((label, url) in actions) {
            root.addView(MaterialButton(this).apply {
                this.text = label
                setOnClickListener {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                        .onFailure {
                            android.widget.Toast.makeText(
                                this@OpenSourceLicensesActivity, "No browser available",
                                android.widget.Toast.LENGTH_SHORT,
                            ).show()
                        }
                }
            })
        }
        setMonogatariContent(ScrollView(this).apply { addView(root) }, MonogatariChrome.TEXT_MAX_WIDTH_DP)
    }
}
