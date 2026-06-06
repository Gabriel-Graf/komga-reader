package com.komgareader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key-Value-Settings (id = Key). */
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

/**
 * Einzelne Server-Verbindung (id fix = 1 im MVP).
 * Credentials liegen AES/GCM-verschlüsselt (Keystore-Schlüssel) direkt in dieser Tabelle:
 * *Ciphertext* und *IV* als Base64-Strings. Klartext verlässt nie die Keystore-Grenze.
 */
@Entity(tableName = "server")
data class ServerEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val baseUrl: String,
    val username: String? = null,
    /** Base64-kodiertes AES/GCM-Chiffrat des API-Keys, oder null wenn nicht gesetzt. */
    val apiKeyCiphertext: String? = null,
    /** Base64-kodierter IV (Nonce) zum API-Key-Chiffrat. */
    val apiKeyIv: String? = null,
    /** Base64-kodiertes AES/GCM-Chiffrat des Passworts, oder null wenn nicht gesetzt. */
    val passwordCiphertext: String? = null,
    /** Base64-kodierter IV (Nonce) zum Passwort-Chiffrat. */
    val passwordIv: String? = null,
)

/** Nutzer-definierte App-Bibliothek. [sources] kodiert (siehe ShelfSourceCodec). */
@Entity(tableName = "shelves")
data class ShelfEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val sources: String,
    val defaultContentType: String? = null,
)

/** Lokal gespeichertes Buch (Download-Eintrag). */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val bookRemoteId: String,
    val sourceId: Long,
    val seriesRemoteId: String,
    val title: String,
    val format: String,
    val localPath: String,
    val totalPages: Int,
)

/** Persistiertes E-Ink-Farbfilter-Profil. */
@Entity(tableName = "color_profiles")
data class ColorProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val builtIn: Boolean,
)
