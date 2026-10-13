package org.koitharu.kotatsu.core.qa

/** Step 63: conservative matching; notification delivery is intentionally not enabled. */
internal object FollowedTagPolicy {
    fun matches(candidateTags: Collection<String>, followedTags: Set<String>): Boolean =
        followedTags.isNotEmpty() && candidateTags.any { tag ->
            followedTags.any { followed -> tag.trim().equals(followed.trim(), ignoreCase = true) }
        }
}
