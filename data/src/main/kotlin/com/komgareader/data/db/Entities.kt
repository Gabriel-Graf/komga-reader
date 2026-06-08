package com.komgareader.data.db

import androidx.room.ColumnInfo
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
    /** Quellenart (`SourceKind`-Name: "KOMGA" | "OPDS" | …); Default KOMGA für Bestandsdaten. */
    @ColumnInfo(defaultValue = "KOMGA") val kind: String = "KOMGA",
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

/**
 * Manuell zugewiesener Inhaltstyp eines Werks (Serie). Quellen-übergreifend per
 * [sourceId] + [seriesRemoteId]. [contentType] ist ein [com.komgareader.domain.model.ContentType]-Name.
 */
@Entity(tableName = "series_overrides", primaryKeys = ["sourceId", "seriesRemoteId"])
data class SeriesOverrideEntity(
    val sourceId: Long,
    val seriesRemoteId: String,
    val contentType: String,
)

/**
 * Lokaler Lesefortschritt je Buch (offline-first). [dirty] = noch nicht zum Server gepusht.
 * Wird mit dem Server-Stand gemerged (höhere Seite gewinnt, kein Regress).
 */
@Entity(tableName = "read_progress")
data class ReadProgressEntity(
    @PrimaryKey val bookRemoteId: String,
    val sourceId: Long,
    val page: Int,
    val completed: Boolean,
    val totalPages: Int,
    val dirty: Boolean,
    val updatedAt: Long,
)

/**
 * Lokaler Roman-Lesefortschritt (ViewerType.NOVEL, crengine-Reflow). Quellen-übergreifend
 * per [sourceId] + [bookId]. Der [anchor] (crengine-Xpointer) ist die **exakte**,
 * Schrift- und Viewport-unabhängige Leseposition für das Wiederaufnehmen auf demselben Gerät;
 * [fraction] (0.0..1.0) ist der grobe Anteil für den geräteübergreifenden %-Sync zu Komga.
 * [dirty] = lokal geändert, noch nicht zum Server gepusht.
 */
@Entity(tableName = "novel_progress", primaryKeys = ["sourceId", "bookId"])
data class NovelProgressEntity(
    val sourceId: Long,
    val bookId: String,
    val anchor: String,
    val fraction: Float,
    val dirty: Boolean,
    val updatedAt: Long,
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
    val seriesTitle: String = "",
    val seriesCoverUrl: String? = null,
)

/** Persistiertes E-Ink-Farbfilter-Profil. */
@Entity(tableName = "color_profiles")
data class ColorProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val saturation: Float,
    val contrast: Float,
    val brightness: Float,
    val blackPoint: Float = 0f,
    val whitePoint: Float = 1f,
    val gamma: Float = 1f,
    val sharpenAmount: Float = 0f,
    val sharpenRadius: Int = 1,
    val ditherMode: String = "NONE",
    val ditherLevels: Int = 16,
    val builtIn: Boolean,
)
