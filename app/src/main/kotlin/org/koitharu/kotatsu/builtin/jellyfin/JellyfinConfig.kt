package org.koitharu.kotatsu.builtin.jellyfin

import org.koitharu.kotatsu.core.keys.ApiKeys
import org.koitharu.kotatsu.core.keys.ApiServices
import javax.inject.Inject
import javax.inject.Singleton

/** The user's own Jellyfin server address and API key, read from the in-app key store (More > API keys & accounts). */
@Singleton
class JellyfinConfig @Inject constructor(
	private val keys: ApiKeys,
) {

	val server: String? get() = keys.get(ApiServices.JELLYFIN, "server")

	val apiKey: String? get() = keys.get(ApiServices.JELLYFIN, "api_key")

	val isConfigured: Boolean get() = server != null && apiKey != null
}
