package org.koitharu.kotatsu.discover.media

import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as materialR

/** Colours the pull-to-refresh indicator from the current theme: primary spinner on a surface-coloured disc. */
fun SwipeRefreshLayout.applyThemeColors() {
	setColorSchemeColors(MaterialColors.getColor(this, materialR.attr.colorPrimary))
	setProgressBackgroundColorSchemeColor(MaterialColors.getColor(this, materialR.attr.colorSurface))
}
