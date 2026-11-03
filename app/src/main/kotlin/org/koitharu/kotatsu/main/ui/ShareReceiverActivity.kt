package org.koitharu.kotatsu.main.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koitharu.kotatsu.builtin.BuiltinSource
import org.koitharu.kotatsu.core.nav.AppRouter
import org.koitharu.kotatsu.core.parser.MangaLinkResolver
import org.koitharu.kotatsu.parsers.model.MangaListFilter
import org.koitharu.kotatsu.parsers.util.runCatchingCancellable
import javax.inject.Inject

/**
 * Target of "Share" (ACTION_SEND, text/plain) from browsers, YouTube and other apps.
 *
 * Only routes the shared text to an existing screen, then finishes. It never downloads or installs
 * anything, and it never logs the shared text. Repository links are handed to MainActivity, which
 * asks the user for consent before adding anything.
 */
@AndroidEntryPoint
class ShareReceiverActivity : FragmentActivity() {

	@Inject
	lateinit var linkResolver: MangaLinkResolver

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val incoming = intent
		if (savedInstanceState != null || incoming?.action != Intent.ACTION_SEND) {
			finish()
			return
		}
		val text = try {
			incoming.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
		} catch (_: RuntimeException) {
			null
		}
		val router = AppRouter(this)
		when (val result = SharedLinkClassifier.classify(text)) {
			SharedLinkClassifier.Result.Empty -> finish()

			is SharedLinkClassifier.Result.RepoSetup -> {
				openSetupLink(Uri.parse(result.link))
				finish()
			}

			is SharedLinkClassifier.Result.RepoIndex -> {
				openSetupLink(
					Uri.Builder()
						.scheme("monogatari")
						.authority("setup")
						.appendQueryParameter("repos", result.url)
						.build(),
				)
				finish()
			}

			is SharedLinkClassifier.Result.YouTube -> {
				router.openList(BuiltinSource.YOUTUBE, MangaListFilter(query = result.query), null)
				finish()
			}

			is SharedLinkClassifier.Result.Text -> {
				router.openMainSearch("ALL", result.text)
				finish()
			}

			is SharedLinkClassifier.Result.WebLink -> lifecycleScope.launch {
				// Resolving may need the network, so give up after a while and fall back to a plain search.
				val manga = withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
					runCatchingCancellable { linkResolver.resolve(Uri.parse(result.url)) }.getOrNull()
				}
				if (manga != null) {
					router.openDetails(manga)
				} else {
					router.openMainSearch("ALL", result.text)
				}
				finish()
			}
		}
	}

	/** MainActivity validates the link and shows its consent dialog; nothing is added from here. */
	private fun openSetupLink(link: Uri) {
		startActivity(
			Intent(this, MainActivity::class.java)
				.setAction(Intent.ACTION_VIEW)
				.setData(link)
				.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
		)
	}

	private companion object {

		const val RESOLVE_TIMEOUT_MS = 15_000L
	}
}
