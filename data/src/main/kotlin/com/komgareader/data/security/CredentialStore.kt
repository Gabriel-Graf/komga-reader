package com.komgareader.data.security

/** Sichere Ablage für Geheimnisse (API-Keys). Implementierung Keystore-gestützt. */
interface CredentialStore {
    fun getApiKey(): String?
    fun setApiKey(value: String)
    fun clear()
}
