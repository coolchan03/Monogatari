package org.koitharu.kotatsu.core.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URI

/**
 * Opens links that come from websites, feeds and catalogs (untrusted text) outside the app.
 *
 * Only absolute http(s) addresses with a host are opened, as a BROWSABLE intent, so a provider page or
 * feed cannot use `intent:`, `file:`, `content:` or custom-scheme links to start other apps' components.
 */
object ExternalLinks {

	private val ALLOWED_SCHEMES = setOf("http", "https")

	/** Pure check, usable from tests and from places that only decide whether to show a button. */
	fun isAllowed(url: String?): Boolean {
		if (url.isNullOrBlank() || url.length > 8192) return false
		val parsed = try {
			URI(url.trim())
		} catch (_: Exception) {
			return false
		}
		return parsed.scheme?.lowercase() in ALLOWED_SCHEMES && !parsed.host.isNullOrBlank() && parsed.userInfo == null
	}

	/**
	 * @return false when the link was refused or no browser could open it; callers show their own message.
	 */
	fun open(context: Context, url: String?): Boolean {
		if (!isAllowed(url)) return false
		val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url!!.trim())).addCategory(Intent.CATEGORY_BROWSABLE)
		if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
		return try {
			context.startActivity(intent)
			true
		} catch (_: ActivityNotFoundException) {
			false
		} catch (_: SecurityException) {
			false
		}
	}
}
