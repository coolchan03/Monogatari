package org.koitharu.kotatsu.core.ui

import android.content.Context
import android.widget.TextView
import androidx.core.view.ViewCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors

/** Shared Material typography and cards for More, source diagnostics, and standalone hubs. */
object MonogatariSectionStyle {
    fun heading(context: Context): TextView = TextView(context).apply {
        val density = resources.displayMetrics.density
        setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall)
        setTextColor(MaterialColors.getColor(this, androidx.appcompat.R.attr.colorPrimary))
        setPadding((8 * density).toInt(), (16 * density).toInt(), 0, (4 * density).toInt())
        ViewCompat.setAccessibilityHeading(this, true)
    }

    fun card(card: MaterialCardView) {
        val density = card.resources.displayMetrics.density
        card.radius = 18 * density
        card.cardElevation = 0f
        card.strokeWidth = density.toInt().coerceAtLeast(1)
        card.strokeColor = MaterialColors.getColor(card, com.google.android.material.R.attr.colorOutlineVariant)
        card.setCardBackgroundColor(MaterialColors.getColor(card, com.google.android.material.R.attr.colorSurfaceContainerLow))
    }
}
