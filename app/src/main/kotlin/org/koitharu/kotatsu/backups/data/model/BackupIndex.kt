package org.koitharu.kotatsu.backups.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.koitharu.kotatsu.BuildConfig

@Serializable
class BackupIndex(
	@SerialName("app_id") val appId: String,
	@SerialName("app_version") val appVersion: Int,
	@SerialName("created_at") val createdAt: Long,
    @SerialName("device_id") val deviceId: String = "",
    @SerialName("device_name") val deviceName: String = "",
) {

    constructor(deviceId: String = "", deviceName: String = "") : this(
        appId = BuildConfig.APPLICATION_ID,
        appVersion = BuildConfig.VERSION_CODE,
        createdAt = System.currentTimeMillis(),
        deviceId = deviceId,
        deviceName = deviceName,
    )
}
