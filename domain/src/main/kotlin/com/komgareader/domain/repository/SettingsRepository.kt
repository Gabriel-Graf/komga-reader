package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** App-weite Einstellungen (Theme, Sprache) als Strings (UI-neutral). */
interface SettingsRepository {
    val themeMode: Flow<String>   // "LIGHT" | "DARK" | "SYSTEM"
    val language: Flow<String>    // "de" | "en"
    val displayMode: Flow<String> // "EINK" | "SMARTPHONE"
    suspend fun setThemeMode(value: String)
    suspend fun setLanguage(value: String)
    suspend fun setDisplayMode(value: String)
}
