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

	/** Seed compatible JS indexes once; never re-add a repo after the user removes it. */
	fun getAll(): List<String> = (prefs.getStringSet(KEY_URLS, null) ?: defaultRepos()).sorted()

	/** @return `false` if [url] is not a valid https URL. Adding an existing one is a no-op success. */
	fun add(url: String): Boolean {
		val normalized = normalizeRepoUrl(url) ?: return false
		prefs.edit { putStringSet(KEY_URLS, getAll().toSet() + normalized) }
		return true
	}

	fun remove(url: String) {
		prefs.edit { putStringSet(KEY_URLS, getAll().toSet() - url) }
	}

	private fun defaultRepos(): Set<String> = BuildConfig.DEFAULT_REPOS.split(',')
		.map { it.trim() }.filter { normalizeRepoUrl(it) != null }.toSet()

	private companion object {

		const val KEY_URLS = "urls"

	}
}
