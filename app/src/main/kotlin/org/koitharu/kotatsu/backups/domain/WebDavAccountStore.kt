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

/** Android Keystore encrypts credentials; preference must never be backed up. */
@Singleton
internal class WebDavAccountStore @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("monogatari_webdav_secret", Context.MODE_PRIVATE)
    private val alias = "monogatari_webdav_aes_v1"

    fun isConfigured(): Boolean = runCatching { load() != null }.getOrDefault(false)

    fun save(baseUrl: String, username: String, password: String) {
        val endpoint = WebDavEndpoint.parse(baseUrl)
        require(username.isNotBlank() && password.isNotBlank())
        val json = JSONObject().put("url", baseUrl).put("user", username).put("password", password)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(json.toString().toByteArray(Charsets.UTF_8))
        check(prefs.edit().putString("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString("payload", Base64.encodeToString(encrypted, Base64.NO_WRAP)).commit())
        endpoint.collection // validate before persisting
    }

    fun load(): WebDavAccount? {
        val iv = prefs.getString("iv", null) ?: return null
        val payload = prefs.getString("payload", null) ?: return null
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT)))
        val data = JSONObject(String(cipher.doFinal(Base64.decode(payload, Base64.DEFAULT)), Charsets.UTF_8))
        return WebDavAccount(WebDavEndpoint.parse(data.getString("url")),
            WebDavAuth(data.getString("user"), data.getString("password")))
    }

    fun clear() { prefs.edit().clear().commit() }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256).build())
        }.generateKey()
    }
}
