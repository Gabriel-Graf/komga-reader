package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** App-weite Einstellungen (Theme, Sprache) als Strings (UI-neutral). */
interface SettingsRepository {
    val themeMode: Flow<String>   // "LIGHT" | "DARK" | "SYSTEM"
    val language: Flow<String>    // "de" | "en"
    suspend fun setThemeMode(value: String)
    suspend fun setLanguage(value: String)
}
