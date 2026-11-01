package org.koitharu.kotatsu.sourcerepo.data

import android.content.Context
import org.koitharu.kotatsu.BuildConfig
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import org.koitharu.kotatsu.sourcerepo.domain.normalizeRepoUrl
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The list of repo index URLs the user added. Own prefs file so the feature stays out of
 * [org.koitharu.kotatsu.core.prefs.AppSettings].
 */
@Singleton
class SourceRepoStore @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences("source_repos", Context.MODE_PRIVATE)

	/** Seeds each default repo once (see [RepoSeeding]); never re-adds a repo the user removed. */
	@Synchronized
	fun getAll(): List<String> {
		val stored = prefs.getStringSet(KEY_URLS, null)
		val seeded = prefs.getStringSet(KEY_SEEDED, null)
		val buildTime = RecommendedRepos.parseList(BuildConfig.DEFAULT_REPOS)
		val result = RepoSeeding.seed(stored, seeded, RecommendedRepos.mergedDefaults(BuildConfig.DEFAULT_REPOS), buildTime)
		if (result.urls != stored || result.seeded != seeded) {
			prefs.edit {
				putStringSet(KEY_URLS, result.urls)
				putStringSet(KEY_SEEDED, result.seeded)
			}
		}
		return result.urls.sorted()
	}

	/** @return `false` if [url] is not a valid https URL. Adding an existing one is a no-op success. */
	@Synchronized
	fun add(url: String): Boolean {
		val normalized = normalizeRepoUrl(url) ?: return false
		val urls = getAll().toSet() + normalized
		prefs.edit { putStringSet(KEY_URLS, urls) }
		return true
	}

	/** Leaves [KEY_SEEDED] alone so the repo is not offered again. */
	@Synchronized
	fun remove(url: String) {
		val urls = getAll().toSet() - url
		prefs.edit { putStringSet(KEY_URLS, urls) }
	}

	private companion object {

		const val KEY_URLS = "urls"
		const val KEY_SEEDED = "seeded"

	}
}
