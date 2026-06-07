package com.komgareader.domain.repository

import kotlinx.coroutines.flow.Flow

/** App-weite Einstellungen (Theme, Sprache, Anzeige-Modus, Download-Ordner) als Strings (UI-neutral). */
interface SettingsRepository {
    val themeMode: Flow<String>      // "LIGHT" | "DARK" | "SYSTEM"
    val language: Flow<String>       // "de" | "en"
    val displayMode: Flow<String>    // "EINK" | "SMARTPHONE"
    val downloadDir: Flow<String?>   // SAF-Tree-URI oder null (= interner App-Speicher)
    val guidedPanelOverlay: Flow<Boolean>   // Debug: erkannte Panel-Rahmen im Comic-Reader einblenden
    val activeColorProfileId: Flow<Long?>  // id des aktiven Farbfilter-Profils, null = noch keines gesetzt
    val webtoonOverlapPercent: Flow<Int>  // Überlappung zwischen Webtoon-Streifen in Prozent (0–50)
    val chapterViewMode: Flow<String>  // "LIST" | "GRID" — Kapitel als Textliste oder Cover-Gitter
    suspend fun setThemeMode(value: String)
    suspend fun setLanguage(value: String)
    suspend fun setDisplayMode(value: String)
    suspend fun setDownloadDir(uri: String?)
    suspend fun setGuidedPanelOverlay(value: Boolean)
    suspend fun setActiveColorProfileId(id: Long)
    suspend fun setWebtoonOverlapPercent(percent: Int)
    suspend fun setChapterViewMode(mode: String)
}
