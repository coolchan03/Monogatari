package org.koitharu.kotatsu.core.qa

/** Step 65: deny every secondary exposure while a private category is locked. */
internal object PrivateCategoryPolicy {
    enum class Surface { LIBRARY, WIDGET, DISCORD, CLOUD_TRANSLATION, SCREENSHOT }
    fun allowed(isPrivate: Boolean, unlocked: Boolean, surface: Surface): Boolean =
        !isPrivate || (unlocked && surface == Surface.LIBRARY)
}
