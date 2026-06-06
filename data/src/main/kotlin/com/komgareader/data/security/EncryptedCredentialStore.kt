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
 * neu erstellt. Scheitert auch das, fällt der Store auf einen reinen **In-Memory-Speicher**
 * zurück (Secrets nur im RAM für die Session) — Credentials werden **niemals unverschlüsselt
 * auf Platte** geschrieben. Die App stürzt nicht ab; der Nutzer muss ggf. neu anmelden.
 */
class EncryptedCredentialStore(
    private val context: Context,
    private val prefsFileName: String = "komga-reader-secrets",
) : CredentialStore {

    /** Verschlüsselte Prefs, oder null wenn Verschlüsselung nicht verfügbar ist. */
    private val prefs: android.content.SharedPreferences? = openPrefs(isRetry = false)

    /** Nur-RAM-Fallback, wenn [prefs] null ist — verlässt nie den Prozess. */
    private val memory = mutableMapOf<String, String>()

    private fun openPrefs(isRetry: Boolean): android.content.SharedPreferences? {
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
                // KEIN Klartext-Fallback für Geheimnisse — nur RAM (kein Persistieren).
                Log.e(TAG, "EncryptedSharedPreferences nicht verfügbar — Secrets nur im Speicher (keine Persistenz)", e)
                null
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

    private fun read(key: String): String? =
        if (prefs != null) runCatching { prefs.getString(key, null) }.getOrNull() else memory[key]

    private fun write(key: String, value: String) {
        if (prefs != null) runCatching { prefs.edit().putString(key, value).commit() }
        else memory[key] = value
    }

    override fun getApiKey(): String? = read(KEY_API)
    override fun setApiKey(value: String) = write(KEY_API, value)

    override fun getPassword(): String? = read(KEY_PASSWORD)
    override fun setPassword(value: String) = write(KEY_PASSWORD, value)

    override fun clear() {
        if (prefs != null) runCatching { prefs.edit().remove(KEY_API).remove(KEY_PASSWORD).commit() }
        memory.remove(KEY_API)
        memory.remove(KEY_PASSWORD)
    }

    private companion object {
        const val TAG = "EncryptedCredentialStore"
        const val KEY_API = "komga_api_key"
        const val KEY_PASSWORD = "komga_password"
    }
}
