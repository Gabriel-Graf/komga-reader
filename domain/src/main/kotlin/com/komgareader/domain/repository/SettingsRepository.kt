package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** App-weite Einstellungen (Theme, Sprache, Download-Ordner) als Strings (UI-neutral). */
interface SettingsRepository {
    val themeMode: Flow<String>      // "LIGHT" | "DARK" | "SYSTEM"
    val language: Flow<String>       // "de" | "en"
    val downloadDir: Flow<String?>   // SAF-Tree-URI oder null (= interner App-Speicher)
    suspend fun setThemeMode(value: String)
    suspend fun setLanguage(value: String)
    suspend fun setDownloadDir(uri: String?)
}
