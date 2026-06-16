package com.komgareader.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
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
    // Rowid (Long). Früher fix 1 (Single-Server); jetzt mehrere Verbindungen. Int→Long ist in
    // SQLite dieselbe INTEGER-Affinität → KEIN Schema-Change, keine Migration nötig.
    @PrimaryKey val id: Long = 0,
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
    /** Base64-kodiertes AES/GCM-Chiffrat des extras-JSON-Blobs, oder null wenn leer. */
    val extrasCiphertext: String? = null,
    /** Base64-kodierter IV (Nonce) zum extras-Chiffrat. */
    val extrasIv: String? = null,
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
 * Auto-detected content-type suggestion of a series (pixel heuristic). Separate from
 * [SeriesOverrideEntity]: a heuristic guess the user/server/library all outrank.
 * [contentType] is a [com.komgareader.domain.model.ContentType] name; [detectorVersion]
 * makes re-detection idempotent across algorithm bumps.
 */
@Entity(tableName = "series_auto_types", primaryKeys = ["sourceId", "seriesRemoteId"])
data class SeriesAutoTypeEntity(
    val sourceId: Long,
    val seriesRemoteId: String,
    val contentType: String,
    val detectorVersion: Int,
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

/**
 * Local-only in-text bookmark in a reflowable novel ([NovelBookmarkEntity] mirrors
 * [com.komgareader.domain.model.NovelBookmark]). Never synced. Scoped per [sourceId] +
 * [bookId]; [xpointer] is the crengine xpointer, [snippet] the captured surrounding text.
 */
@Entity(
    tableName = "novel_bookmark",
    indices = [Index(value = ["sourceId", "bookId"])],
)
data class NovelBookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceId: Long,
    val bookId: String,
    val xpointer: String,
    val number: Int,
    val label: String?,
    val snippet: String,
    val createdAt: Long,
    // defaultValue MUST match the MIGRATION_19_20 ALTER defaults verbatim, else Room's
    // schema validation fails and fallbackToDestructiveMigration wipes the DB
    // (see room-migration-destructive-pitfall). "-16777216" == 0xFF000000 (opaque black).
    @ColumnInfo(defaultValue = "FLAG") val markerStyle: String = "FLAG",
    @ColumnInfo(defaultValue = "-16777216") val color: Int = 0xFF000000.toInt(),
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
    val pluginPackage: String? = null,
)

/** Vom Nutzer hinzugefügtes Plugin-Repository (URL + optionaler Name). */
@Entity(tableName = "plugin_repos", indices = [Index(value = ["url"], unique = true)])
data class PluginRepoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val name: String? = null,
)

/**
 * One finished reading session (append-only log). [readerKind] is a
 * [com.komgareader.domain.model.ReaderKind] name; time aggregates are derived from these rows.
 * Local-only — never synced to a server.
 */
@Entity(tableName = "reading_session")
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val readerKind: String,
    val bookRemoteId: String,
    val sourceId: Long,
    val startTs: Long,
    val durationMs: Long,
)
