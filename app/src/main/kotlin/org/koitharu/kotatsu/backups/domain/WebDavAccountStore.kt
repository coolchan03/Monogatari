package org.koitharu.kotatsu.backups.domain

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal data class WebDavAccount(val endpoint: WebDavEndpoint, val auth: WebDavAuth)

/** Keystore-backed secrets. This preference file is excluded from Android backup. */
@Singleton
internal class WebDavAccountStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("monogatari_webdav_secret", Context.MODE_PRIVATE)

    fun isConfigured(): Boolean = runCatching { load() != null }.getOrDefault(false)
    // Disabled until encrypted credential persistence is implemented and verified.
    fun load(): WebDavAccount? = null
}
