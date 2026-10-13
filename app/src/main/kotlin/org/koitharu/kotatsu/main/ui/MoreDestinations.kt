package org.koitharu.kotatsu.main.ui

import android.content.Intent
import androidx.appcompat.app.AlertDialog
import org.koitharu.kotatsu.backups.domain.WebDavManualSyncActivity
import org.koitharu.kotatsu.core.media.BookLibraryActivity
import org.koitharu.kotatsu.core.media.UnifiedLibraryActivity
import org.koitharu.kotatsu.core.qa.MonogatariTestCenterActivity
import org.koitharu.kotatsu.core.qa.NewsFeedActivity
import org.koitharu.kotatsu.settings.SettingsActivity
import org.koitharu.kotatsu.stats.ui.StatsActivity

/** Centralized More destinations, keeping the main activity focused on navigation. */
internal object MoreDestinations {
    private val pages = listOf(
        "Unified Library" to UnifiedLibraryActivity::class.java,
        "Books catalog" to BookLibraryActivity::class.java,
        "Reading statistics" to StatsActivity::class.java,
        "News & followed tags" to NewsFeedActivity::class.java,
        "Manual WebDAV backups" to WebDavManualSyncActivity::class.java,
        "Source test center" to MonogatariTestCenterActivity::class.java,
        "Settings" to SettingsActivity::class.java,
    )

    fun show(activity: MainActivity) {
        AlertDialog.Builder(activity)
            .setTitle("More")
            .setItems(pages.map { it.first }.toTypedArray()) { _, selected ->
                activity.startActivity(Intent(activity, pages[selected].second))
            }
            .show()
    }
}
