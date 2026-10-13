package org.koitharu.kotatsu.backups.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Metadata only. Executable JS is downloaded from a configured repository on restore. */
@Serializable
data class JsSourceBackup(
    @SerialName("id") val id: Long,
    @SerialName("repo_url") val repoUrl: String,
    @SerialName("version") val version: String,
)
