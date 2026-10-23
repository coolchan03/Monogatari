package org.koitharu.kotatsu.core.ui

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.annotation.DrawableRes
import com.google.android.material.color.MaterialColors

/**
 * One palette-aware renderer for the small Kitsune stickers. Only their teal details change
 * to the selected theme's primary colour; white fur, skin and gold remain unchanged.
 * The primary colour is read from the view's own theme, so colour schemes, the custom accent
 * and Material You (dynamic colour) all reach the art.
 */
internal object MascotArtwork {
    private val cache = android.util.LruCache<String, Bitmap>(28)

    fun bind(view: ImageView, @DrawableRes resource: Int) {
        val themeColour = MaterialColors.getColor(view, androidx.appcompat.R.attr.colorPrimary)
        val night = isNight(view)
        val key = "${resource}:${themeColour}:${night}"
        val bitmap = synchronized(cache) { cache.get(key) } ?: recolour(view, resource, themeColour, night)
        if (bitmap != null) {
            synchronized(cache) { cache.put(key, bitmap) }
            view.setImageBitmap(bitmap)
        } else view.setImageResource(resource)
    }

    fun isNight(view: ImageView): Boolean =
        view.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private fun recolour(view: ImageView, @DrawableRes resource: Int, accent: Int, night: Boolean): Bitmap? {
        val original = BitmapFactory.decodeResource(view.resources, resource) ?: return null
        val result = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val data = IntArray(original.width * original.height)
        original.getPixels(data, 0, original.width, 0, 0, original.width, original.height)
        val tint = MascotTint(accent, night)
        for (i in data.indices) {
            data[i] = tint.apply(data[i])
        }
        result.setPixels(data, 0, original.width, 0, 0, original.width, original.height)
        original.recycle()
        return result
    }
}

/**
 * Pure per-pixel tint for the Kitsune art (no Android graphics classes, so it is unit-tested).
 *
 * - Teal/aqua details take the theme's hue. A nearly grey primary (monochrome schemes, or the black/white accent
 *   fallback on Android 11 and older) turns them neutral instead of borrowing the meaningless hue of grey, which
 *   used to paint Kitsune red.
 * - In dark themes (including AMOLED black) bright white fur is dimmed slightly so the sticker does not glare
 *   against a dark surface; all other colours keep their values.
 */
internal class MascotTint(primary: Int, private val night: Boolean) {

    private val target = rgbToHsv(primary)
    private val neutral = target[1] < NEUTRAL_SATURATION

    fun apply(pixel: Int): Int {
        val alpha = pixel ushr 24
        if (alpha < 8) return pixel
        val hsv = rgbToHsv(pixel)
        var changed = false
        // Only shift aqua/teal sections: leave white fur, skin and gold alone.
        if (hsv[0] in 158f..211f && hsv[1] > 0.22f) {
            if (neutral) {
                hsv[1] = hsv[1] * 0.15f
            } else {
                hsv[0] = target[0]
                hsv[1] = (hsv[1] * 0.7f + target[1] * 0.3f).coerceIn(0.25f, 1f)
            }
            if (night) hsv[2] = hsv[2] * 0.92f
            changed = true
        } else if (night && hsv[1] < 0.12f && hsv[2] > 0.9f) {
            hsv[2] = hsv[2] * NIGHT_WHITE
            changed = true
        }
        return if (changed) hsvToRgb(alpha, hsv) else pixel
    }

    companion object {

        private const val NEUTRAL_SATURATION = 0.15f
        private const val NIGHT_WHITE = 0.88f

        fun rgbToHsv(color: Int): FloatArray {
            val r = (color shr 16 and 0xFF) / 255f
            val g = (color shr 8 and 0xFF) / 255f
            val b = (color and 0xFF) / 255f
            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min
            val hue = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * ((b - r) / delta + 2f)
                else -> 60f * ((r - g) / delta + 4f)
            }.let { if (it < 0f) it + 360f else it }
            val saturation = if (max == 0f) 0f else delta / max
            return floatArrayOf(hue, saturation, max)
        }

        fun hsvToRgb(alpha: Int, hsv: FloatArray): Int {
            val h = ((hsv[0] % 360f) + 360f) % 360f
            val s = hsv[1].coerceIn(0f, 1f)
            val v = hsv[2].coerceIn(0f, 1f)
            val c = v * s
            val x = c * (1f - kotlin.math.abs((h / 60f) % 2f - 1f))
            val m = v - c
            val (r, g, b) = when ((h / 60f).toInt()) {
                0 -> Triple(c, x, 0f)
                1 -> Triple(x, c, 0f)
                2 -> Triple(0f, c, x)
                3 -> Triple(0f, x, c)
                4 -> Triple(x, 0f, c)
                else -> Triple(c, 0f, x)
            }
            fun channel(value: Float) = ((value + m) * 255f + 0.5f).toInt().coerceIn(0, 255)
            return (alpha shl 24) or (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
        }
    }
}
