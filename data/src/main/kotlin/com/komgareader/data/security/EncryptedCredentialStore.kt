package com.komgareader.data.security

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Speichert Geheimnisse in EncryptedSharedPreferences, verschlüsselt mit einem
 * Android-Keystore-MasterKey (AES256-GCM). Klartext-Secrets liegen nie auf Platte.
 */
class EncryptedCredentialStore(context: Context) : CredentialStore {

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "komga-reader-secrets",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun getApiKey(): String? = prefs.getString(KEY_API, null)
    override fun setApiKey(value: String) = prefs.edit().putString(KEY_API, value).apply()
    override fun clear() = prefs.edit().remove(KEY_API).apply()

    private companion object { const val KEY_API = "komga_api_key" }
}
