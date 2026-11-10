package org.koitharu.kotatsu.jsext.repo

import java.io.IOException
import java.net.URI
import java.util.Locale

/**
 * Thrown when an install would hand an already installed extension to a different publisher
 * (repository) than the one it came from, and the user has not approved that transfer.
 */
class PublisherChangeException(
	val sourceId: Long,
	val installedPublisher: String,
	val candidatePublisher: String,
) : IOException("Extension $sourceId is published by $installedPublisher; refusing code from $candidatePublisher without approval")

/** A higher-or-equal version of an installed source offered by a repository other than its publisher. */
data class PublisherChangeOffer(
	val installed: JsSourceEntry,
	val candidate: JsSourceEntry,
)

/**
 * Publisher provenance for JS extensions, modelled on Mangayomi's repository-ownership fix: the
 * repository an extension was first installed from is its publisher, updates are only taken from
 * that publisher, and a transfer to another repository needs explicit approval.
 */
object JsPublisherPolicy {

	private val GITHUB_HOSTS = setOf("raw.githubusercontent.com", "github.com", "gist.githubusercontent.com")

	/**
	 * Stable identity of a repository index URL: scheme-less, lowercase host plus the repository path.
	 * Branch and index file name are ignored for GitHub hosts so `main` -> `master` or
	 * `index.json` -> `index.min.json` is not treated as a new publisher. Exact host match only.
	 */
	fun publisherKey(repoUrl: String): String {
		val uri = runCatching { URI(repoUrl.trim()) }.getOrNull() ?: return repoUrl.trim().lowercase(Locale.ROOT)
		val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return repoUrl.trim().lowercase(Locale.ROOT)
		val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
		val kept = if (host in GITHUB_HOSTS) {
			segments.take(2).map { it.lowercase(Locale.ROOT) }
		} else {
			// Drop a trailing file name (anything with a dot) so /repo/index.json == /repo/
			segments.dropLastWhile { '.' in it }
		}
		return host + kept.joinToString("") { "/$it" }
	}

	/**
	 * Legacy installs recorded before provenance tracking may have a blank repo URL; their publisher
	 * is unknown, so they are treated as unpinned and adopt whichever repo updates them next.
	 */
	fun isSamePublisher(installedRepoUrl: String, candidateRepoUrl: String): Boolean {
		if (installedRepoUrl.isBlank()) return true
		if (candidateRepoUrl.isBlank()) return false
		return publisherKey(installedRepoUrl) == publisherKey(candidateRepoUrl)
	}

	/**
	 * Throws [PublisherChangeException] unless [candidate] comes from the installed entry's
	 * publisher or the transfer was explicitly approved. Call under the store lock.
	 */
	fun requireSamePublisher(installed: JsSourceEntry?, candidate: JsSourceEntry, approved: Boolean) {
		if (installed == null || approved) return
		if (!isSamePublisher(installed.repoUrl, candidate.repoUrl)) {
			throw PublisherChangeException(
				sourceId = candidate.id,
				installedPublisher = publisherKey(installed.repoUrl),
				candidatePublisher = publisherKey(candidate.repoUrl),
			)
		}
	}

	/** Result of choosing what to offer for one source id. */
	data class Selection(
		/** Best runnable entry to display/update; for an installed source it is from its publisher. */
		val entry: JsSourceEntry?,
		/** Runnable entries from other publishers that are not eligible for silent update. */
		val foreign: List<JsSourceEntry>,
	)

	/**
	 * Picks the entry to offer from [group] (all entries sharing one id across repositories).
	 * With no installed copy (or an unpinned legacy one) the newest runnable version wins, as
	 * before. Otherwise only the installed publisher's entries compete and other publishers'
	 * entries are returned as [Selection.foreign].
	 */
	fun select(group: List<JsSourceEntry>, installed: JsSourceEntry?): Selection {
		val runnable = group.filter { it.isRunnable }
		fun newest(list: List<JsSourceEntry>) = list.maxWithOrNull { a, b -> compareVersions(a.version, b.version) }
		if (installed == null || installed.repoUrl.isBlank()) return Selection(newest(runnable), emptyList())
		val (same, foreign) = runnable.partition { isSamePublisher(installed.repoUrl, it.repoUrl) }
		return Selection(newest(same), foreign)
	}

	/**
	 * Same id advertised by several publishers. Not an error by itself (mirrors are common), but a
	 * conflict when versions or content hashes disagree, which is what an impersonation looks like.
	 */
	fun hasSuspiciousConflict(group: List<JsSourceEntry>): Boolean {
		val publishers = group.map { publisherKey(it.repoUrl) }.distinct()
		if (publishers.size < 2) return false
		val sameVersionDifferentHash = group.groupBy { it.version }.values.any { sameVersion ->
			sameVersion.mapNotNull { it.sha256?.lowercase(Locale.ROOT) }.distinct().size > 1
		}
		val names = group.map { it.name.trim().lowercase(Locale.ROOT) }.distinct().size > 1
		val baseUrls = group.map { runCatching { URI(it.baseUrl).host?.lowercase(Locale.ROOT) }.getOrNull() }.distinct().size > 1
		return sameVersionDifferentHash || names || baseUrls
	}
}
