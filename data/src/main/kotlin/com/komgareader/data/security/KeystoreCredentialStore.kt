package com.komgareader.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Verschlüsselt und entschlüsselt Geheimnisse mit einem AES/GCM-Schlüssel aus dem
 * AndroidKeyStore. Der Schlüssel überlebt App-Updates (`adb install -r`), da er im
 * Hardware-Keystore verankert ist und NICHT an die APK-Signatur gebunden wird.
 *
 * Ciphertext und IV werden von [encrypt] als [CipherBlob] zurückgegeben. Der Aufrufer
 * (typisch: [com.komgareader.data.repository.RoomServerRepository]) persistiert diese
 * Blobs in Room — so überleben Credentials sowohl Prozessneustarts als auch App-Updates.
 */
class KeystoreCredentialStore(
    private val keyAlias: String = KEY_ALIAS,
) : CredentialStore {

    /**
     * Kompakte Darstellung eines verschlüsselten Werts.
     * Beide Felder sind Base64-kodiert (NO_WRAP) und können direkt in Room gespeichert werden.
     */
    data class CipherBlob(val ciphertext: String, val iv: String)

    /** Verschlüsselt [plaintext] und gibt einen persistierbaren [CipherBlob] zurück. */
    fun encrypt(plaintext: String): CipherBlob {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val cipherBytes = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return CipherBlob(
            ciphertext = Base64.encodeToString(cipherBytes, Base64.NO_WRAP),
            iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
    }

    /**
     * Entschlüsselt [blob] mit dem AndroidKeyStore-Schlüssel.
     * Gibt null zurück, wenn der Schlüssel fehlt oder die Daten beschädigt sind.
     */
    fun decrypt(blob: CipherBlob): String? = try {
        val key = loadKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val iv = Base64.decode(blob.iv, Base64.NO_WRAP)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(Base64.decode(blob.ciphertext, Base64.NO_WRAP)), Charsets.UTF_8)
    } catch (e: Exception) {
        Log.e(TAG, "Entschlüsselung fehlgeschlagen — Credential nicht lesbar", e)
        null
    }

    // CredentialStore-Implementierung — delegiert an In-Memory-Puffer,
    // da Persistenz über RoomServerRepository läuft.
    private val memory = mutableMapOf<String, String>()
    override fun getApiKey(): String? = memory[KEY_API]
    override fun setApiKey(value: String) { memory[KEY_API] = value }
    override fun getPassword(): String? = memory[KEY_PASSWORD]
    override fun setPassword(value: String) { memory[KEY_PASSWORD] = value }
    override fun clear() { memory.remove(KEY_API); memory.remove(KEY_PASSWORD) }

    private fun getOrCreateKey(): SecretKey = loadKey() ?: run {
        val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        keyGen.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        keyGen.generateKey()
    }

    private fun loadKey(): SecretKey? {
        val ks = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        return (ks.getEntry(keyAlias, null) as? KeyStore.SecretKeyEntry)?.secretKey
    }

    companion object {
        const val TAG = "KeystoreCredentialStore"
        const val KEY_ALIAS = "komga_cred_key"
        const val KEY_API = "komga_api_key"
        const val KEY_PASSWORD = "komga_password"
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }
}
