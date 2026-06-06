package com.komgareader.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [SettingEntity::class, ServerEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun serverDao(): ServerDao
}
