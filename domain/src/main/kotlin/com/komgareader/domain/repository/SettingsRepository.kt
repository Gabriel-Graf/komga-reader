package com.komgareader.domain.repository

import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import kotlinx.coroutines.flow.Flow

/** App-weite Einstellungen (Theme, Sprache, Anzeige-Modus, Download-Ordner) als Strings (UI-neutral). */
interface SettingsRepository {
    val themeMode: Flow<String>      // "LIGHT" | "DARK" | "SYSTEM"
    val language: Flow<String>       // "de" | "en"
    val displayMode: Flow<String>    // "EINK" | "SMARTPHONE"
    val shellLayoutMode: Flow<String>  // "AUTO" | "COMPACT" | "EXPANDED" — Home-Skelett-Form-Faktor (Override)
    /** "UNDERLINE" | "MARGIN" | "FLAG" — default marker style for *new* novel bookmarks (existing carry their own). */
    val bookmarkMarkerStyle: Flow<String>
    /** "ASK" | "IMPORT" | "READ_ONLY" — what to do when an external book file is opened. */
    val externalOpenBehavior: Flow<String>
    val downloadDir: Flow<String?>   // SAF-Tree-URI oder null (= interner App-Speicher)
    val guidedPanelOverlay: Flow<Boolean>   // Debug: erkannte Panel-Rahmen im Comic-Reader einblenden
    /** Comic-Guided: Panel-Erkennung per ML-Modell-Plugin (PANEL_MODEL); aus = Geometrie-Fallback. Default true. */
    val useMlDetection: Flow<Boolean>
    val activeColorProfileId: Flow<Long?>  // id des aktiven Farbfilter-Profils, null = noch keines gesetzt
    val webtoonOverlapPercent: Flow<Int>  // Überlappung zwischen Webtoon-Streifen in Prozent (0–50)
    val chapterViewMode: Flow<String>  // "LIST" | "GRID" — Kapitel als Textliste oder Cover-Gitter
    val librariesViewMode: Flow<String>    // "LIST" | "TILE" | "LARGE_TILE" — Bibliotheken-Tab (Default LIST)
    val collectionsViewMode: Flow<String>  // "LIST" | "TILE" | "LARGE_TILE" — Sammlungen-Tab (Default LARGE_TILE)
    // Roman-Reader-Typografie (global, gilt für alle Romane). Strings/Floats UI-neutral;
    // der Mapper NovelSettings.toReflowConfig() setzt sie in eine ReflowConfig um.
    val novelFontSizeEm: Flow<Float>          // Schriftgröße in em (1.0 = Basis)
    val novelLineHeight: Flow<Float>          // Zeilenhöhe als Faktor (1.0 = einfach)
    val novelMarginPreset: Flow<String>       // "NARROW" | "NORMAL" | "WIDE"
    val novelFontFamily: Flow<String>         // Registrierter Familienname (gebündelt, z.B. "DejaVu Sans")
    val novelTextAlign: Flow<String>          // "LEFT" | "JUSTIFY"
    val novelHyphenationLang: Flow<String>    // "" = aus, sonst Sprachcode ("de"/"en")
    val novelFontWeight: Flow<Int>            // Grund-Schriftstärke (400 = normal, höher = dicker)
    /** Ob das offizielle Plugin-Repo im Browser geladen wird (Default true). */
    val officialRepoEnabled: Flow<Boolean>
    /** packageName des aktiven UI-Packs (data-only Plugin-Kategorie UI_PACK); "" = keiner (Host-Default). */
    val activeUiPack: Flow<String>
    /** Last app version (versionName) the user has seen. "" = never set (first run). Basis for the
     *  "what's new" modal: shows the release notes exactly once after a version bump. */
    val lastSeenVersion: Flow<String>
    /** Per-context E-Ink mode overrides; unset axes fall back to the device default. */
    val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>>
    /** Persisted frontlight brightness level (0–100). -1 = never set (leave device current). */
    val frontlightLevel: Flow<Int>
    suspend fun setThemeMode(value: String)
    suspend fun setLanguage(value: String)
    suspend fun setDisplayMode(value: String)
    suspend fun setShellLayoutMode(value: String)
    suspend fun setBookmarkMarkerStyle(value: String)
    suspend fun setExternalOpenBehavior(value: String)
    suspend fun setDownloadDir(uri: String?)
    suspend fun setGuidedPanelOverlay(value: Boolean)
    suspend fun setUseMlDetection(value: Boolean)
    suspend fun setActiveColorProfileId(id: Long)
    suspend fun setWebtoonOverlapPercent(percent: Int)
    suspend fun setChapterViewMode(mode: String)
    suspend fun setLibrariesViewMode(mode: String)
    suspend fun setCollectionsViewMode(mode: String)
    suspend fun setNovelFontSizeEm(value: Float)
    suspend fun setNovelLineHeight(value: Float)
    suspend fun setNovelMarginPreset(preset: String)
    suspend fun setNovelFontFamily(family: String)
    suspend fun setNovelTextAlign(align: String)
    suspend fun setNovelHyphenationLang(lang: String)
    suspend fun setNovelFontWeight(value: Int)
    suspend fun setOfficialRepoEnabled(enabled: Boolean)
    suspend fun setActiveUiPack(packageName: String)
    suspend fun setLastSeenVersion(version: String)
    suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile)
    suspend fun setFrontlightLevel(level: Int)
}
