package org.koitharu.kotatsu.core.keys

import org.koitharu.kotatsu.builtin.jellyfin.JellyfinRepository

/** A service that needs the user's own credentials, with where to get them (pure and unit-tested). */
data class ApiService(
	val id: String,
	val title: String,
	val summary: String,
	/** Where to sign up or find the credential. */
	val signupUrl: String,
	val fields: List<ApiField>,
)

/** [kind] drives validation: a "server" is normalised into a clean http(s) address. */
data class ApiField(val key: String, val label: String, val secret: Boolean, val kind: String = "text", val hint: String = "")

internal object ApiServices {

	const val JELLYFIN = "jellyfin"
	const val PLEX = "plex"
	const val TWITCH = "twitch"
	const val VIMEO = "vimeo"
	const val AUDIOBOOKSHELF = "audiobookshelf"
	const val SUBSONIC = "subsonic"

	val all = listOf(
		ApiService(
			JELLYFIN, "Jellyfin (your own server)",
			"Your home media server. In its dashboard open Dashboard > API Keys and create one.",
			"https://jellyfin.org/",
			listOf(
				ApiField("server", "Server address", false, "server", "192.168.1.5:8096"),
				ApiField("api_key", "API key", true),
			),
		),
		ApiService(
			PLEX, "Plex (your own server)",
			"Your Plex Media Server. The token is the X-Plex-Token Plex's support page explains how to find.",
			"https://support.plex.tv/articles/204059436-finding-an-authentication-token-x-plex-token/",
			listOf(
				ApiField("server", "Server address", false, "server", "192.168.1.5:32400"),
				ApiField("token", "Plex token", true),
			),
		),
		ApiService(
			AUDIOBOOKSHELF, "Audiobookshelf (your own server)",
			"Your audiobook and podcast server. In its web app open Settings > Users, pick your user and copy the API token.",
			"https://www.audiobookshelf.org/",
			listOf(
				ApiField("server", "Server address", false, "server", "192.168.1.5:13378"),
				ApiField("token", "API token", true),
			),
		),
		ApiService(
			SUBSONIC, "Subsonic / Navidrome (your own server)",
			"Your music server (Navidrome, Gonic, Airsonic or Subsonic). Use the username and password you sign in with there; the password is never sent as it is, only a salted hash.",
			"https://www.navidrome.org/",
			listOf(
				ApiField("server", "Server address", false, "server", "192.168.1.5:4533"),
				ApiField("username", "Username", false, "text"),
				ApiField("password", "Password", true),
			),
		),
		ApiService(
			TWITCH, "Twitch",
			"Lists channels, live streams and videos through Twitch's official API. Register a free application to get a Client ID and secret. Playback happens on twitch.tv.",
			"https://dev.twitch.tv/console/apps",
			listOf(
				ApiField("client_id", "Client ID", false),
				ApiField("client_secret", "Client secret", true),
			),
		),
		ApiService(
			VIMEO, "Vimeo",
			"Search public Vimeo videos, or browse channel:12345, category:animation, and showcase:USER_ID/SHOWCASE_ID in Video search. Requires your own public-scope access token. Playback opens vimeo.com.",
			"https://developer.vimeo.com/apps",
			listOf(ApiField("access_token", "Access token", true)),
		),
	)

	fun byId(id: String): ApiService? = all.firstOrNull { it.id == id }

	/** "saved (…1234)" style hint so a stored secret can be recognised without showing it. */
	fun mask(value: String?): String {
		if (value.isNullOrBlank()) return ""
		return if (value.length <= 6) "••••" else "••••" + value.takeLast(4)
	}

	sealed class Check {
		data class Ok(val values: Map<String, String>) : Check()
		data class Problem(val field: String, val reason: String) : Check()
	}

	/** Trims every value, normalises server addresses and requires every field. */
	fun validate(service: ApiService, input: Map<String, String>): Check {
		val out = LinkedHashMap<String, String>()
		for (field in service.fields) {
			val raw = input[field.key].orEmpty().trim()
			if (raw.isEmpty()) return Check.Problem(field.key, "${field.label} is required")
			// A pasted line break would end up in an HTTP header (and in the error message that reports it).
			if (raw.any { it.isISOControl() }) return Check.Problem(field.key, "${field.label} contains an invalid character")
			out[field.key] = if (field.kind == "server") {
				JellyfinRepository.normalizeServer(raw) ?: return Check.Problem(field.key, "${field.label} isn't a valid address")
			} else raw
		}
		return Check.Ok(out)
	}
}
