package org.koitharu.kotatsu.core.qa

/** Maps AniList relation labels to searchable library links, without opening a web source. */
internal object CrossMediaRelationPolicy {
    enum class Action { WATCH_ANIME, READ_NOVEL, FIND_SEQUEL }
    fun action(type: String, format: String): Action? = when {
        type.equals("SEQUEL", true) -> Action.FIND_SEQUEL
        format.equals("TV", true) || format.equals("MOVIE", true) ||
            format.equals("ONA", true) -> Action.WATCH_ANIME
        format.equals("NOVEL", true) -> Action.READ_NOVEL
        else -> null
    }
    fun query(title: String): String? = DiscoveryQueryPolicy.findCopyQuery(title)
}
