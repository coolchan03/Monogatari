package org.koitharu.kotatsu.core.keys

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The user's own API keys and tokens, kept in this app's private storage on the device. They are entered in the app
 * (More > API keys & accounts) and are never part of the source code or the GitHub repository. Like the rest of the
 * app's settings they may travel with the user's own device backups.
 */
@Singleton
class ApiKeys @Inject constructor(
	@ApplicationContext context: Context,
) {

	private val prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

	fun get(service: String, field: String): String? = prefs.getString("$service.$field", null)?.takeIf { it.isNotBlank() }

	fun set(service: String, values: Map<String, String>) {
		prefs.edit { values.forEach { (field, value) -> putString("$service.$field", value) } }
	}

	fun clear(service: String) {
		prefs.edit { prefs.all.keys.filter { it.startsWith("$service.") }.forEach { remove(it) } }
	}

	fun isConfigured(service: String): Boolean {
		val definition = ApiServices.byId(service) ?: return false
		return definition.fields.all { get(service, it.key) != null }
	}

	companion object {

		const val FILE = "monogatari_api_keys"
	}
}
