package com.komgareader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SettingEntity::class, ServerEntity::class, DownloadEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun serverDao(): ServerDao
    abstract fun downloadDao(): DownloadDao
}
