package org.koitharu.kotatsu.sourcehealth

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.jsext.repo.JsSourceStore
import org.koitharu.kotatsu.jsext.source.JsMangaSource
import org.koitharu.kotatsu.sourcerepo.domain.JsSourceItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks sources that disappear from an otherwise successfully fetched extension catalog.
 * Never deletes source code, saved titles, categories, preferences or reading history.
 * Missing sources remain manually accessible through the source-management screen.
 */
@Singleton
class SourceRetirementTracker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val installed: JsSourceStore,
) {
    private val prefs by lazy {
        context.getSharedPreferences("monogatari_retired_sources", Context.MODE_PRIVATE)
    }

    fun recordCompleteSnapshot(
        catalog: List<JsSourceItem>,
        configuredRepositories: Set<String>,
        now: Long = System.currentTimeMillis(),
    ) {
        if (catalog.isEmpty() || configuredRepositories.isEmpty()) return
        val seenIds = catalog.mapTo(HashSet()) { it.entry.id }
        val editor = prefs.edit()
        for (entry in installed.list()) {
            if (entry.repoUrl !in configuredRepositories) continue
            val key = "missing_${entry.id}"
            if (entry.id in seenIds) {
                editor.remove(key)
            } else if (!prefs.contains(key)) {
                editor.putLong(key, now)
            }
        }
        editor.apply()
    }

    fun retiredNames(now: Long = System.currentTimeMillis()): Set<String> =
        installed.list().mapNotNull { entry ->
            val missingSince = prefs.getLong("missing_${entry.id}", 0L)
            JsMangaSource(entry.id).name.takeIf {
                SourceRetirementPolicy.isRetired(missingSince, now)
            }
        }.toSet()
}

internal object SourceRetirementPolicy {
    const val GRACE_PERIOD_MS = 30L * 24 * 60 * 60 * 1000

    fun isRetired(missingSince: Long, now: Long): Boolean =
        missingSince > 0L && now >= missingSince && now - missingSince >= GRACE_PERIOD_MS
}
