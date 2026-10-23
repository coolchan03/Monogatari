package org.koitharu.kotatsu.backups.data

/**
 * Category ids are generated per device, so an incoming id says nothing about which local category it is.
 * Categories are matched by title (case-insensitive) instead, and favourites are remapped to the result.
 */
internal object BackupCategoryMergePlan {

    class LocalCategory(val id: Long, val title: String, val deletedAt: Long)

    sealed interface Action {
        /** Use the existing local category; its own fields are left untouched. */
        class Reuse(val localId: Long) : Action

        /** Insert as a new category with a fresh local id. */
        object Insert : Action

        /** Ignore it (an incoming deletion that matches nothing local). */
        object Skip : Action
    }

    fun resolve(incomingTitle: String, incomingDeletedAt: Long, local: List<LocalCategory>): Action {
        val match = local.firstOrNull { it.deletedAt == 0L && it.title.trim().equals(incomingTitle.trim(), ignoreCase = true) }
        return when {
            match != null -> Action.Reuse(match.id)
            incomingDeletedAt != 0L -> Action.Skip
            else -> Action.Insert
        }
    }

    /**
     * The local category for an incoming favourite, or null if it must be skipped. [remap] is null when the
     * archive had no categories section to resolve against; the incoming id is then used as is.
     */
    fun remap(remap: Map<Long, Long>?, incomingCategoryId: Long): Long? =
        if (remap == null) incomingCategoryId else remap[incomingCategoryId]
}
