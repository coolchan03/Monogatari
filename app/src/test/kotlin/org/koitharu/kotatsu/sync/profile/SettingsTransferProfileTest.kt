package org.koitharu.kotatsu.sync.profile

import org.junit.Assert.assertEquals
import org.junit.Test
import org.koitharu.kotatsu.sync.profile.SettingsTransferProfile.Group

class SettingsTransferProfileTest {

	@Test
	fun groupsKeysByMeaning() {
		assertEquals(Group.APPEARANCE, SettingsTransferProfile.groupOf("monogatari_accent_color"))
		assertEquals(Group.READER, SettingsTransferProfile.groupOf("reader_mode"))
		assertEquals(Group.READER, SettingsTransferProfile.groupOf("webtoon_zoom"))
		assertEquals(Group.SOURCES, SettingsTransferProfile.groupOf("monogatari_hide_adult_sources"))
		assertEquals(Group.NETWORK, SettingsTransferProfile.groupOf("doh"))
		assertEquals(Group.NETWORK, SettingsTransferProfile.groupOf("tracker_wifi"))
		assertEquals(Group.TRACKING, SettingsTransferProfile.groupOf("tracker_freq"))
		assertEquals(Group.OTHER, SettingsTransferProfile.groupOf("exit_confirm"))
	}

	@Test
	fun neverTransfersSecretsOrDeviceFolders() {
		val all = mapOf(
			"theme" to 1, "reader_mode" to 2, "app_password" to 3, "proxy_password" to 4, "proxy_address" to 5,
			"discord_token" to 6, "local_manga_dirs" to 7,
		)
		assertEquals(mapOf("theme" to 1, "reader_mode" to 2), SettingsTransferProfile.filter(all, setOf(Group.APPEARANCE, Group.READER)))
		assertEquals(mapOf("proxy_address" to 5), SettingsTransferProfile.filter(all, Group.entries.toSet()))
	}
}
