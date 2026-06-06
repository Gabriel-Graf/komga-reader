package com.komgareader.data.security

/** Sichere Ablage für Geheimnisse (API-Keys und Passwörter). Implementierung Keystore-gestützt. */
interface CredentialStore {
    fun getApiKey(): String?
    fun setApiKey(value: String)
    fun getPassword(): String?
    fun setPassword(value: String)
    fun clear()
}
