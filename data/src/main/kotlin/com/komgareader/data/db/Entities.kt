package com.komgareader.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Key-Value-Settings (id = Key). */
@Entity(tableName = "settings")
data class SettingEntity(@PrimaryKey val key: String, val value: String)

/** Einzelne Server-Verbindung (id fix = 1 im MVP). */
@Entity(tableName = "server")
data class ServerEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val baseUrl: String,
    val apiKey: String,
)
