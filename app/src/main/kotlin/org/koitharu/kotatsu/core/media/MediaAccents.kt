package org.koitharu.kotatsu.core.media

import android.content.Context
import android.content.res.ColorStateList
import androidx.preference.PreferenceManager
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip

/** Opt-in media-aware colors; intentionally dark enough for white legible labels. */
object MediaAccents {
    const val KEY = "monogatari_multicolor"

    fun enabled(context: Context): Boolean =
        PreferenceManager.getDefaultSharedPreferences(context).getBoolean(KEY, false)

    fun color(kind: String): Int = when (kind.uppercase()) {
        "MANGA" -> 0xFF087A6E.toInt()
        "BOOK" -> 0xFF485C9A.toInt()
        "VIDEO" -> 0xFF713A8C.toInt()
        "AUDIO" -> 0xFF935113.toInt()
        "NEWS" -> 0xFF005F91.toInt()
        else -> 0xFF416D73.toInt()
    }

    fun tintChip(context: Context, kind: String, chip: Chip) {
        if (!enabled(context)) return
        chip.chipBackgroundColor = ColorStateList.valueOf(color(kind))
        chip.setTextColor(android.graphics.Color.WHITE)
    }

    fun tintButton(context: Context, kind: String, button: MaterialButton) {
        if (!enabled(context)) return
        button.backgroundTintList = ColorStateList.valueOf(color(kind))
        button.setTextColor(android.graphics.Color.WHITE)
    }
}
