package org.koitharu.kotatsu.search.ui.multi

import android.view.View
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.koitharu.kotatsu.R
import java.util.Locale
import org.koitharu.kotatsu.core.prefs.ReaderMode

/** Hide unsupported filter choices rather than claiming every catalog reports the same metadata. */
internal object SearchMetadataFilterDialog {
    fun show(activity: SearchActivity, viewModel: SearchViewModel) {
        val dp = activity.resources.displayMetrics.density
        val gap = (12 * dp).toInt()
        val content = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(gap * 2, gap, gap * 2, gap)
        }
        fun label(res: Int) {
            content.addView(TextView(activity).apply {
                setText(res)
                setPadding(0, gap, 0, gap / 2)
                setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
            })
        }
        fun spinner(res: Int, labels: List<String>, selectedIndex: Int): Spinner {
            label(res)
            return Spinner(activity).apply {
                adapter = ArrayAdapter(activity, android.R.layout.simple_spinner_item, labels)
                    .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                minimumHeight = (48 * dp).toInt()
                setSelection(selectedIndex.coerceIn(0, labels.lastIndex))
                contentDescription = activity.getString(res)
            }.also { content.addView(it) }
        }
        val availableTags = viewModel.availableGenres()
        val genre = if (availableTags.isNotEmpty() || viewModel.selectedGenre != null) {
            label(R.string.search_genre_tag_label)
            EditText(activity).apply {
                setSingleLine(true)
                setText(viewModel.selectedGenre.orEmpty())
                setHint(R.string.search_genre_tag_hint)
                contentDescription = activity.getString(R.string.search_genre_tag_label)
                content.addView(this)
            }
        } else null
        val languages = (viewModel.languagesInResults() + listOfNotNull(viewModel.selectedLanguage)).distinct()
        val languageOptions = listOf<String?>(null) + languages
        val languagePicker = if (languages.isNotEmpty()) spinner(
            R.string.search_language_label,
            languageOptions.map { it?.let { code -> Locale.forLanguageTag(code).displayLanguage } ?: activity.getString(R.string.search_language_any) },
            languageOptions.indexOf(viewModel.selectedLanguage),
        ) else null
        val formats = (viewModel.availableBookFormats() + listOfNotNull(viewModel.selectedBookFormat)).distinct()
        val formatOptions = listOf<String?>(null) + formats
        val formatPicker = if (formats.isNotEmpty()) spinner(
            R.string.search_format_label,
            formatOptions.map { it ?: activity.getString(R.string.search_language_any) },
            formatOptions.indexOf(viewModel.selectedBookFormat),
        ) else null
        val directions = (viewModel.availableReaderModes().map { it.name } +
            listOfNotNull(viewModel.selectedReadingMode)).distinct()
        val directionOptions = listOf<String?>(null) + directions
        val directionPicker = if (directions.isNotEmpty()) spinner(
            R.string.search_reading_direction,
            directionOptions.map { option -> when (option) {
                "STANDARD" -> activity.getString(R.string.search_reading_ltr)
                "REVERSED" -> activity.getString(R.string.search_reading_rtl)
                "VERTICAL" -> activity.getString(R.string.search_reading_vertical)
                "WEBTOON" -> activity.getString(R.string.search_reading_webtoon)
                else -> activity.getString(R.string.search_language_any)
            } },
            directionOptions.indexOf(viewModel.selectedReadingMode),
        ) else null
        val historyModes = SearchMetadataFilters.History.entries
        val historyPicker = if (viewModel.hasHistoryCapableResults() ||
            viewModel.selectedHistoryFilter != SearchMetadataFilters.History.ANY
        ) spinner(
            R.string.search_local_progress_label,
            listOf(
                activity.getString(R.string.search_history_all),
                activity.getString(R.string.search_history_viewed),
                activity.getString(R.string.search_history_unviewed),
            ),
            historyModes.indexOf(viewModel.selectedHistoryFilter),
        ) else null
        if (genre == null && languagePicker == null && formatPicker == null && historyPicker == null && directionPicker == null) {
            content.addView(TextView(activity).apply { setText(R.string.search_metadata_unavailable) })
        }
        val scroll = ScrollView(activity).apply { addView(content); isFillViewport = false }
        MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.search_metadata_filters)
            .setView(scroll)
            .setNeutralButton(R.string.search_clear_metadata_filters) { _, _ ->
                viewModel.setGenreFilter(null)
                viewModel.setBookFormatFilter(null)
                viewModel.setLanguageFilter(null)
                viewModel.setHistoryFilter(SearchMetadataFilters.History.ANY)
                viewModel.setReadingModeFilter(null)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                genre?.let { viewModel.setGenreFilter(it.text?.toString()) }
                languagePicker?.let { viewModel.setLanguageFilter(languageOptions[it.selectedItemPosition]) }
                formatPicker?.let { viewModel.setBookFormatFilter(formatOptions[it.selectedItemPosition]) }
                historyPicker?.let { viewModel.setHistoryFilter(historyModes[it.selectedItemPosition]) }
                directionPicker?.let { viewModel.setReadingModeFilter(directionOptions[it.selectedItemPosition]) }
            }
            .show()
    }
}
