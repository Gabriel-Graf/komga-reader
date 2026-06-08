package com.komgareader.data.repository

import com.komgareader.data.db.ServerDao
import com.komgareader.data.db.ServerEntity
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.data.security.KeystoreCredentialStore.CipherBlob
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.repository.ServerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-gestützte Implementierung von [ServerRepository]. **Mehrere** Verbindungen gleichzeitig
 * (mehrere Komga, OPDS, später Plugin-Server — beliebig gemischt), jede mit eigener Rowid.
 *
 * Credentials (API-Key, Passwort) werden AES/GCM-verschlüsselt als Blobs direkt in der
 * server-Tabelle abgelegt. Der AndroidKeyStore-Schlüssel überlebt App-Updates — daher
 * überleben auch die Credentials einen `adb install -r`.
 */
class RoomServerRepository(
    private val dao: ServerDao,
    private val credentialStore: KeystoreCredentialStore,
) : ServerRepository {

    override val configs: Flow<List<ServerConfig>> = dao.observeAll().map { list ->
        list.map { it.toConfig() }
    }

    override val config: Flow<ServerConfig?> = configs.map { it.firstOrNull() }

    override suspend fun save(config: ServerConfig) {
        val apiBlob = config.apiKey?.takeIf { it.isNotBlank() }?.let { credentialStore.encrypt(it) }
        val pwBlob = config.password?.takeIf { it.isNotBlank() }?.let { credentialStore.encrypt(it) }
        // Neue Verbindung (id == 0) bekommt die nächste freie Rowid; eine bestehende wird ersetzt.
        val id = if (config.id != 0L) config.id else dao.maxId() + 1
        dao.save(
            ServerEntity(
                id = id,
                name = config.name,
                baseUrl = config.baseUrl,
                kind = config.kind.name,
                username = config.username,
                apiKeyCiphertext = apiBlob?.ciphertext,
                apiKeyIv = apiBlob?.iv,
                passwordCiphertext = pwBlob?.ciphertext,
                passwordIv = pwBlob?.iv,
            ),
        )
    }

    override suspend fun remove(id: Long) {
        dao.delete(id)
    }

    override suspend fun clear() {
        dao.clear()
    }

    private fun ServerEntity.toConfig() = ServerConfig(
        id = id,
        name = name,
        baseUrl = baseUrl,
        apiKey = decryptIfPresent(apiKeyCiphertext, apiKeyIv),
        username = username,
        password = decryptIfPresent(passwordCiphertext, passwordIv),
        kind = runCatching { SourceKind.valueOf(kind) }.getOrDefault(SourceKind.KOMGA),
    )

    private fun decryptIfPresent(ciphertext: String?, iv: String?): String? {
        if (ciphertext == null || iv == null) return null
        val result = credentialStore.decrypt(CipherBlob(ciphertext, iv))
        return result?.takeIf { it.isNotBlank() }
    }
}
