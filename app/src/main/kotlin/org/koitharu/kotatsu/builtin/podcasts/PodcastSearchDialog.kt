package org.koitharu.kotatsu.builtin.podcasts

import android.content.Context
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.target
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.koitharu.kotatsu.R

/**
 * "Find podcasts" dialog: search box, a "Top podcasts" button and result cards with a Subscribe button.
 * [onSubscribe] receives an https feed URL and is expected to validate and store it like a pasted URL.
 */
internal class PodcastSearchDialog(
	private val context: Context,
	private val directory: PodcastDirectory,
	private val imageLoader: ImageLoader,
	private val scope: CoroutineScope,
	private val isSubscribed: (String) -> Boolean,
	private val onSubscribe: (String) -> Unit,
) {
	private lateinit var dialog: AlertDialog
	private lateinit var status: TextView
	private lateinit var list: LinearLayout
	private var job: Job? = null

	private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

	fun show() {
		val root = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(dp(16), dp(8), dp(16), 0)
		}
		val input = EditText(context).apply {
			hint = context.getString(R.string.more_sources_podcasts_hint)
			setSingleLine(true)
			imeOptions = EditorInfo.IME_ACTION_SEARCH
		}
		val buttons = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
		val searchButton = MaterialButton(context).apply { text = context.getString(R.string.more_sources_podcasts_search) }
		val topButton = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
			.apply { text = context.getString(R.string.more_sources_podcasts_top) }
		buttons.addView(searchButton)
		buttons.addView(topButton)
		status = TextView(context).apply { setPadding(0, dp(8), 0, dp(8)) }
		list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
		val scroll = ScrollView(context).apply { addView(list) }
		root.addView(input)
		root.addView(buttons)
		root.addView(status)
		root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(380)))

		val search: suspend () -> List<PodcastDirectoryParser.Podcast> = { directory.search(input.text.toString()) }
		searchButton.setOnClickListener { load(search) }
		topButton.setOnClickListener { load { directory.top() } }
		input.setOnEditorActionListener { _, actionId, _ ->
			if (actionId == EditorInfo.IME_ACTION_SEARCH) {
				load(search)
				true
			} else false
		}
		dialog = MaterialAlertDialogBuilder(context)
			.setTitle(R.string.more_sources_podcasts_find)
			.setView(root)
			.setNegativeButton(android.R.string.cancel, null)
			.create()
		dialog.setOnDismissListener { job?.cancel() }
		dialog.show()
	}

	private fun load(block: suspend () -> List<PodcastDirectoryParser.Podcast>) {
		job?.cancel()
		list.removeAllViews()
		status.text = context.getString(R.string.mono_feeds_loading_feed)
		job = scope.launch {
			try {
				val items = block()
				status.text = if (items.isEmpty()) context.getString(R.string.more_sources_podcasts_empty) else ""
				items.forEach { list.addView(card(it)) }
			} catch (cancel: CancellationException) {
				throw cancel
			} catch (error: Exception) {
				status.text = context.getString(R.string.more_sources_podcasts_error, error.message.orEmpty().take(120))
			}
		}
	}

	private fun card(podcast: PodcastDirectoryParser.Podcast): LinearLayout {
		val row = LinearLayout(context).apply {
			orientation = LinearLayout.HORIZONTAL
			gravity = Gravity.CENTER_VERTICAL
			setPadding(0, dp(6), 0, dp(6))
		}
		val cover = ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
		row.addView(cover, LinearLayout.LayoutParams(dp(64), dp(64)))
		podcast.artworkUrl?.let { url ->
			imageLoader.enqueue(ImageRequest.Builder(context).data(url).target(cover).build())
		}
		val texts = LinearLayout(context).apply {
			orientation = LinearLayout.VERTICAL
			setPadding(dp(12), 0, dp(8), 0)
		}
		texts.addView(TextView(context).apply {
			text = podcast.title
			maxLines = 2
			setTypeface(typeface, android.graphics.Typeface.BOLD)
		})
		texts.addView(TextView(context).apply {
			text = listOfNotNull(podcast.author.takeIf { it.isNotBlank() }, podcast.genre).joinToString(" - ")
			maxLines = 2
		})
		row.addView(texts, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
		val button = MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle)
		if (isSubscribed(podcast.feedUrl)) {
			button.text = context.getString(R.string.more_sources_podcasts_subscribed)
			button.isEnabled = false
		} else {
			button.text = context.getString(R.string.more_sources_podcasts_subscribe)
			button.setOnClickListener {
				dialog.dismiss()
				onSubscribe(podcast.feedUrl)
			}
		}
		row.addView(button)
		return row
	}
}
