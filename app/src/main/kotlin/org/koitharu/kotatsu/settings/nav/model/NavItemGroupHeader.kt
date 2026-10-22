package org.koitharu.kotatsu.settings.nav.model

import androidx.annotation.StringRes

/** A section title in the list of navigation items that can be added. */
data class NavItemGroupHeader(
	@StringRes val title: Int,
)
