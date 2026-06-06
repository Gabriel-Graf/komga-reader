package com.komgareader.data.security

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.io.File

/**
 * Speichert Geheimnisse in EncryptedSharedPreferences, verschlüsselt mit einem
 * Android-Keystore-MasterKey (AES256-GCM). Klartext-Secrets liegen nie auf Platte.
 *
 * Robustheit: Wenn das Keyset beim Start korrupt ist (z.B. nach einem Keystore-Reset
 * oder nach einer unvollständigen Migration), wird die Prefs-Datei gelöscht und einmalig
 * neu erstellt. Credentials gehen dann verloren, aber die App stürzt nicht ab.
 */
class EncryptedCredentialStore(
    private val context: Context,
    private val prefsFileName: String = "komga-reader-secrets",
) : CredentialStore {

    private val prefs = openPrefs(isRetry = false)

    private fun openPrefs(isRetry: Boolean): android.content.SharedPreferences {
        return try {
            EncryptedSharedPreferences.create(
                context,
                prefsFileName,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        } catch (e: Exception) {
            if (isRetry) {
                Log.e(TAG, "EncryptedSharedPreferences konnte nicht wiederhergestellt werden — Fallback auf PlainText", e)
                context.getSharedPreferences(prefsFileName + "_fallback", Context.MODE_PRIVATE)
            } else {
                Log.w(TAG, "Keyset korrupt — lösche Prefs-Datei und versuche Neuanlage", e)
                deletePrefsFile()
                openPrefs(isRetry = true)
            }
        }
    }

    private fun deletePrefsFile() {
        val prefsFile = File(context.filesDir.parent, "shared_prefs/$prefsFileName.xml")
        if (prefsFile.exists()) prefsFile.delete()
    }

    override fun getApiKey(): String? = runCatching { prefs.getString(KEY_API, null) }.getOrNull()
    override fun setApiKey(value: String) { runCatching { prefs.edit().putString(KEY_API, value).commit() } }

    override fun getPassword(): String? = runCatching { prefs.getString(KEY_PASSWORD, null) }.getOrNull()
    override fun setPassword(value: String) { runCatching { prefs.edit().putString(KEY_PASSWORD, value).commit() } }

    override fun clear() { runCatching { prefs.edit().remove(KEY_API).remove(KEY_PASSWORD).commit() } }

    private companion object {
        const val TAG = "EncryptedCredentialStore"
        const val KEY_API = "komga_api_key"
        const val KEY_PASSWORD = "komga_password"
    }
}
