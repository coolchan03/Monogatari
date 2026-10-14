package org.koitharu.kotatsu.builtin.jellyfin

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** The user's own Jellyfin server address and API key, kept only on this device. */
@Singleton
class JellyfinConfig @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences("jellyfin", Context.MODE_PRIVATE)

	val server: String? get() = prefs.getString(KEY_SERVER, null)?.takeIf { it.isNotBlank() }

	val apiKey: String? get() = prefs.getString(KEY_API_KEY, null)?.takeIf { it.isNotBlank() }

	val isConfigured: Boolean get() = server != null && apiKey != null

	fun save(server: String, apiKey: String) {
		prefs.edit {
			putString(KEY_SERVER, server)
			putString(KEY_API_KEY, apiKey)
		}
	}

	fun clear() {
		prefs.edit { clear() }
	}

	private companion object {

		const val KEY_SERVER = "server"
		const val KEY_API_KEY = "api_key"
	}
}
