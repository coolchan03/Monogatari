package org.koitharu.kotatsu.sourcehealth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.card.MaterialCardView
import dagger.hilt.android.AndroidEntryPoint
import org.koitharu.kotatsu.R
import org.koitharu.kotatsu.core.model.MangaSource
import org.koitharu.kotatsu.core.model.getTitle
import org.koitharu.kotatsu.core.ui.MonogatariChrome
import org.koitharu.kotatsu.core.ui.setMonogatariContent
import javax.inject.Inject

/** What each source did recently, with a status per source and a way to open its website when it misbehaves. */
@AndroidEntryPoint
class SourceLogActivity : AppCompatActivity() {

	@Inject
	lateinit var log: SourceActivityLog

	private lateinit var filter: EditText
	private lateinit var summary: TextView
	private lateinit var list: LinearLayout

	// Open site, Copy log and Clear are toolbar menu actions, like the other Monogatari tool screens.
	override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
		menu.add(0, MENU_OPEN_SITE, 0, R.string.source_log_open_site)
		menu.add(0, MENU_COPY, 1, R.string.source_log_copy)
		menu.add(0, MENU_CLEAR, 2, R.string.source_log_clear)
		return true
	}

	override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
		when (item.itemId) {
			MENU_OPEN_SITE -> chooseSite()
			MENU_COPY -> copyLog()
			MENU_CLEAR -> { log.clear(); render() }
			else -> return super.onOptionsItemSelected(item)
		}
		return true
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setTitle(R.string.source_log_title)
		val pad = (12 * resources.displayMetrics.density).toInt()
		val root = LinearLayout(this).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(pad, pad, pad, pad)
		}
		filter = EditText(this).apply {
			setHint(R.string.source_log_filter)
			setSingleLine(true)
			doAfterTextChanged { render() }
		}
		root.addView(filter, LinearLayout.LayoutParams(-1, -2))
		summary = TextView(this)
		root.addView(summary, LinearLayout.LayoutParams(-1, -2))
		list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
		root.addView(ScrollView(this).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))
		setMonogatariContent(root, MonogatariChrome.TEXT_MAX_WIDTH_DP)
		render()
	}

	override fun onResume() {
		super.onResume()
		render()
	}

	private fun render() {
		val now = System.currentTimeMillis()
		val query = filter.text?.toString()?.trim().orEmpty()
		val entries = log.snapshot().filter { query.isBlank() || it.source.contains(query, true) || it.message?.contains(query, true) == true }
		val summaries = SourceLogFormat.summarize(entries)
		summary.text = if (summaries.isEmpty()) {
			getString(R.string.source_log_empty)
		} else {
			summaries.joinToString("\n") { s ->
				val lastOk = s.lastOkMs?.let { SourceLogFormat.ago(now, it) } ?: "never"
				"${titleOf(s.source)}: ${s.status} (last worked: $lastOk)"
			}
		}
		list.removeAllViews()
		val pad = (10 * resources.displayMetrics.density).toInt()
		for (entry in entries.take(150)) {
			val card = MaterialCardView(this).apply {
				radius = 12 * resources.displayMetrics.density
				cardElevation = 0f
				setContentPadding(pad, pad, pad, pad)
			}
			card.addView(TextView(this).apply { text = SourceLogFormat.line(entry, now).replace(entry.source, titleOf(entry.source)) })
			list.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = pad / 2 })
		}
	}

	private fun titleOf(name: String): String = runCatching { MangaSource(name).getTitle(this) }.getOrDefault(name)

	private fun copyLog() {
		val now = System.currentTimeMillis()
		val text = log.snapshot().joinToString("\n") { SourceLogFormat.line(it, now) }
		(getSystemService(CLIPBOARD_SERVICE) as ClipboardManager).setPrimaryClip(ClipData.newPlainText("Source log", text))
		Toast.makeText(this, R.string.source_log_copied, Toast.LENGTH_SHORT).show()
	}

	/** Lists the sources seen so far that have a known website, and opens the chosen one in the browser. */
	private fun chooseSite() {
		val sites = log.snapshot().map { it.source }.distinct().mapNotNull { name ->
			val domain = runCatching { SourceWebsiteGroups.key(MangaSource(name)) }.getOrNull()?.substringBefore('|')
			domain?.let { titleOf(name) to it }
		}
		if (sites.isEmpty()) {
			Toast.makeText(this, R.string.source_log_no_site, Toast.LENGTH_SHORT).show()
			return
		}
		AlertDialog.Builder(this)
			.setTitle(R.string.source_log_open_site)
			.setItems(sites.map { "${it.first} (${it.second})" }.toTypedArray()) { _, which ->
				runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://${sites[which].second}"))) }
			}
			.setNegativeButton(android.R.string.cancel, null)
			.show()
	}

	private companion object {
		const val MENU_OPEN_SITE = 7301
		const val MENU_COPY = 7302
		const val MENU_CLEAR = 7303
	}
}
