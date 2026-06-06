package com.komgareader.data.repository

import com.komgareader.data.db.ServerDao
import com.komgareader.data.db.ServerEntity
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.data.security.KeystoreCredentialStore.CipherBlob
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-gestützte Implementierung von [ServerRepository].
 *
 * Credentials (API-Key, Passwort) werden AES/GCM-verschlüsselt als Blobs direkt in der
 * server-Tabelle abgelegt. Der AndroidKeyStore-Schlüssel überlebt App-Updates — daher
 * überleben auch die Credentials einen `adb install -r`.
 */
class RoomServerRepository(
    private val dao: ServerDao,
    private val credentialStore: KeystoreCredentialStore,
) : ServerRepository {

    override val config: Flow<ServerConfig?> = dao.observe().map { e ->
        e?.let {
            ServerConfig(
                name = it.name,
                baseUrl = it.baseUrl,
                apiKey = decryptIfPresent(it.apiKeyCiphertext, it.apiKeyIv),
                username = it.username,
                password = decryptIfPresent(it.passwordCiphertext, it.passwordIv),
            )
        }
    }

    override suspend fun save(config: ServerConfig) {
        val apiBlob = config.apiKey?.takeIf { it.isNotBlank() }?.let { credentialStore.encrypt(it) }
        val pwBlob = config.password?.takeIf { it.isNotBlank() }?.let { credentialStore.encrypt(it) }
        dao.save(
            ServerEntity(
                name = config.name,
                baseUrl = config.baseUrl,
                username = config.username,
                apiKeyCiphertext = apiBlob?.ciphertext,
                apiKeyIv = apiBlob?.iv,
                passwordCiphertext = pwBlob?.ciphertext,
                passwordIv = pwBlob?.iv,
            ),
        )
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun decryptIfPresent(ciphertext: String?, iv: String?): String? {
        if (ciphertext == null || iv == null) return null
        val result = credentialStore.decrypt(CipherBlob(ciphertext, iv))
        return result?.takeIf { it.isNotBlank() }
    }
}
