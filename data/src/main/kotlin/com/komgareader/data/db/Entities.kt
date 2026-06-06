package com.komgareader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key-Value-Settings (id = Key). */
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

/** Einzelne Server-Verbindung (id fix = 1 im MVP). Kein apiKey – liegt Keystore-verschlüsselt. */
@Entity(tableName = "server")
data class ServerEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val baseUrl: String,
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
