package org.koitharu.kotatsu.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.google.android.material.color.MaterialColors

/**
 * One palette-aware renderer for the small Kitsune stickers. Only their teal details change
 * to the selected theme's primary colour; white fur, skin and gold remain unchanged.
 */
internal object MascotArtwork {
    private val cache = android.util.LruCache<String, Bitmap>(28)

    fun bind(view: ImageView, @DrawableRes resource: Int) {
        val themeColour = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
        val key = "${resource}:${themeColour}"
        val bitmap = synchronized(cache) { cache.get(key) } ?: recolour(view, resource, themeColour)
        if (bitmap != null) {
            synchronized(cache) { cache.put(key, bitmap) }
            view.setImageBitmap(bitmap)
        } else view.setImageResource(resource)
    }

    private fun recolour(view: ImageView, @DrawableRes resource: Int, accent: Int): Bitmap? {
        val original = BitmapFactory.decodeResource(view.resources, resource) ?: return null
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val data = IntArray(original.width * original.height)
        original.getPixels(data, 0, original.width, 0, 0, original.width, original.height)
        val target = FloatArray(3)
        val current = FloatArray(3)
        Color.colorToHSV(accent, target)
        for (i in data.indices) {
            val pixel = data[i]
            if (Color.alpha(pixel) < 8) continue
            Color.colorToHSV(pixel, current)
            // Only shift aqua/teal sections: leave white fur, skin and gold alone.
            if (current[0] in 158f..211f && current[1] > 0.22f) {
                current[0] = target[0]
                current[1] = (current[1] * 0.7f + target[1] * 0.3f).coerceIn(0.25f, 1f)
                data[i] = Color.HSVToColor(Color.alpha(pixel), current)
            }
        }
        result.setPixels(data, 0, original.width, 0, 0, original.width, original.height)
        original.recycle()
        return result
    }
}
