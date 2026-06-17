package com.komgareader.data.repository

import com.komgareader.data.db.SettingEntity
import com.komgareader.data.db.SettingsDao
import com.komgareader.data.eink.decodeEinkContextProfiles
import com.komgareader.data.eink.encodeEinkContextProfiles
import com.komgareader.domain.eink.EinkContext
import com.komgareader.domain.eink.EinkContextProfile
import com.komgareader.domain.model.BookmarkMarkerStyle
import com.komgareader.domain.model.ExternalOpenBehavior
import com.komgareader.domain.model.ScreenSaverMode
import com.komgareader.domain.model.ShellLayoutMode
import com.komgareader.domain.render.NovelFonts
import com.komgareader.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class RoomSettingsRepository(private val dao: SettingsDao) : SettingsRepository {
    override val themeMode: Flow<String> = dao.observe(KEY_THEME).map { it ?: "SYSTEM" }
    override val language: Flow<String> = dao.observe(KEY_LANG).map { it ?: "de" }
    // Default EINK: Ziel-Gerät ist ein Onyx-Boox.
    override val displayMode: Flow<String> = dao.observe(KEY_DISPLAY).map { it ?: "EINK" }
    // Default AUTO: Form-Faktor aus der Bildschirmbreite ableiten (verhaltensgleich zu vorher).
    override val shellLayoutMode: Flow<String> =
        dao.observe(KEY_SHELL_LAYOUT).map { it ?: ShellLayoutMode.AUTO.name }
    // This is now the default marker for *new* bookmarks (existing ones carry their own style).
    override val bookmarkMarkerStyle: Flow<String> =
        dao.observe(KEY_BOOKMARK_MARKER_STYLE).map { it ?: BookmarkMarkerStyle.FLAG.name }
    override val externalOpenBehavior: Flow<String> =
        dao.observe(KEY_EXTERNAL_OPEN_BEHAVIOR).map { it ?: ExternalOpenBehavior.ASK.name }
    override val downloadDir: Flow<String?> = dao.observe(KEY_DOWNLOAD_DIR)
    override val guidedPanelOverlay: Flow<Boolean> = dao.observe(KEY_PANEL_OVERLAY).map { it == "true" }
    // Default true: abwesender Schlüssel oder jeder Wert außer "false" → ML-Erkennung aktiv.
    override val useMlDetection: Flow<Boolean> = dao.observe(KEY_USE_ML).map { it != "false" }
    override val activeColorProfileId: Flow<Long?> = dao.observe(KEY_ACTIVE_COLOR_PROFILE)
        .map { it?.toLongOrNull() }
    override val webtoonOverlapPercent: Flow<Int> =
        dao.observe(KEY_WEBTOON_OVERLAP).map { it?.toIntOrNull() ?: 25 }
    // Default GRID: Kapitel als Cover-Kacheln (nutzt die Buch-Covers).
    override val chapterViewMode: Flow<String> =
        dao.observe(KEY_CHAPTER_VIEW_MODE).map { it ?: "GRID" }
    // Default LIST für Bibliotheken, LARGE_TILE (3er-Gitter) für Sammlungen.
    override val librariesViewMode: Flow<String> =
        dao.observe(KEY_LIBRARIES_VIEW_MODE).map { it ?: "LIST" }
    override val collectionsViewMode: Flow<String> =
        dao.observe(KEY_COLLECTIONS_VIEW_MODE).map { it ?: "LARGE_TILE" }
    // Roman-Typografie: Defaults = lesbare Startwerte (NovelSettings-Defaults).
    override val novelFontSizeEm: Flow<Float> =
        dao.observe(KEY_NOVEL_FONT_SIZE).map { it?.toFloatOrNull() ?: 1.0f }
    override val novelLineHeight: Flow<Float> =
        dao.observe(KEY_NOVEL_LINE_HEIGHT).map { it?.toFloatOrNull() ?: 1.0f }
    override val novelMarginPreset: Flow<String> =
        dao.observe(KEY_NOVEL_MARGIN_PRESET).map { it ?: "NORMAL" }
    override val novelFontFamily: Flow<String> =
        dao.observe(KEY_NOVEL_FONT_FAMILY).map { it ?: NovelFonts.DEFAULT }
    override val novelTextAlign: Flow<String> =
        dao.observe(KEY_NOVEL_TEXT_ALIGN).map { it ?: "JUSTIFY" }
    // Default "auto": resolves to off for unknown/unbundled languages; safe for existing users.
    override val novelHyphenationLang: Flow<String> =
        dao.observe(KEY_NOVEL_HYPHENATION).map { it ?: "auto" }
    override val novelFontWeight: Flow<Int> =
        dao.observe(KEY_NOVEL_FONT_WEIGHT).map { it?.toIntOrNull() ?: 400 }
    // Default true: das offizielle Plugin-Repo wird im Browser standardmäßig geladen.
    override val officialRepoEnabled: Flow<Boolean> =
        dao.observe(KEY_OFFICIAL_REPO_ENABLED).map { it?.toBooleanStrictOrNull() ?: true }
    override val activeUiPack: Flow<String> = dao.observe(KEY_ACTIVE_UI_PACK).map { it ?: "" }
    override val lastSeenVersion: Flow<String> = dao.observe(KEY_LAST_SEEN_VERSION).map { it ?: "" }
    override val einkContextProfiles: Flow<Map<EinkContext, EinkContextProfile>> =
        dao.observe(KEY_EINK_CONTEXT_PROFILES).map { decodeEinkContextProfiles(it) }
    override val screenSaverMode: Flow<String> =
        dao.observe(KEY_SCREENSAVER_MODE).map { it ?: ScreenSaverMode.OFF.name }
    override val screenSaverCustomUri: Flow<String> =
        dao.observe(KEY_SCREENSAVER_CUSTOM_URI).map { it ?: "" }
    override val screenSaverFillCrop: Flow<Boolean> =
        dao.observe(KEY_SCREENSAVER_FILL_CROP).map { it == "true" }

    override suspend fun setThemeMode(value: String) = dao.put(SettingEntity(KEY_THEME, value))
    override suspend fun setLanguage(value: String) = dao.put(SettingEntity(KEY_LANG, value))
    override suspend fun setDisplayMode(value: String) = dao.put(SettingEntity(KEY_DISPLAY, value))
    override suspend fun setShellLayoutMode(value: String) = dao.put(SettingEntity(KEY_SHELL_LAYOUT, value))
    override suspend fun setBookmarkMarkerStyle(value: String) = dao.put(SettingEntity(KEY_BOOKMARK_MARKER_STYLE, value))
    override suspend fun setExternalOpenBehavior(value: String) = dao.put(SettingEntity(KEY_EXTERNAL_OPEN_BEHAVIOR, value))
    override suspend fun setDownloadDir(uri: String?) {
        if (uri == null) dao.delete(KEY_DOWNLOAD_DIR)
        else dao.put(SettingEntity(KEY_DOWNLOAD_DIR, uri))
    }
    override suspend fun setGuidedPanelOverlay(value: Boolean) = dao.put(SettingEntity(KEY_PANEL_OVERLAY, value.toString()))
    override suspend fun setUseMlDetection(value: Boolean) = dao.put(SettingEntity(KEY_USE_ML, value.toString()))
    override suspend fun setWebtoonOverlapPercent(percent: Int) =
        dao.put(SettingEntity(KEY_WEBTOON_OVERLAP, percent.toString()))
    override suspend fun setChapterViewMode(mode: String) =
        dao.put(SettingEntity(KEY_CHAPTER_VIEW_MODE, mode))
    override suspend fun setLibrariesViewMode(mode: String) =
        dao.put(SettingEntity(KEY_LIBRARIES_VIEW_MODE, mode))
    override suspend fun setCollectionsViewMode(mode: String) =
        dao.put(SettingEntity(KEY_COLLECTIONS_VIEW_MODE, mode))
    override suspend fun setNovelFontSizeEm(value: Float) =
        dao.put(SettingEntity(KEY_NOVEL_FONT_SIZE, value.toString()))
    override suspend fun setNovelLineHeight(value: Float) =
        dao.put(SettingEntity(KEY_NOVEL_LINE_HEIGHT, value.toString()))
    override suspend fun setNovelMarginPreset(preset: String) =
        dao.put(SettingEntity(KEY_NOVEL_MARGIN_PRESET, preset))
    override suspend fun setNovelFontFamily(family: String) =
        dao.put(SettingEntity(KEY_NOVEL_FONT_FAMILY, family))
    override suspend fun setNovelTextAlign(align: String) =
        dao.put(SettingEntity(KEY_NOVEL_TEXT_ALIGN, align))
    override suspend fun setNovelHyphenationLang(lang: String) =
        dao.put(SettingEntity(KEY_NOVEL_HYPHENATION, lang))
    override suspend fun setNovelFontWeight(value: Int) =
        dao.put(SettingEntity(KEY_NOVEL_FONT_WEIGHT, value.toString()))
    override suspend fun setOfficialRepoEnabled(enabled: Boolean) =
        dao.put(SettingEntity(KEY_OFFICIAL_REPO_ENABLED, enabled.toString()))
    override suspend fun setActiveUiPack(packageName: String) =
        dao.put(SettingEntity(KEY_ACTIVE_UI_PACK, packageName))
    override suspend fun setScreenSaverMode(value: String) = dao.put(SettingEntity(KEY_SCREENSAVER_MODE, value))
    override suspend fun setScreenSaverCustomUri(uri: String) = dao.put(SettingEntity(KEY_SCREENSAVER_CUSTOM_URI, uri))
    override suspend fun setScreenSaverFillCrop(value: Boolean) = dao.put(SettingEntity(KEY_SCREENSAVER_FILL_CROP, value.toString()))
    override suspend fun setEinkContextProfile(context: EinkContext, profile: EinkContextProfile) {
        val current = decodeEinkContextProfiles(dao.observe(KEY_EINK_CONTEXT_PROFILES).first())
        val next = current + (context to profile)
        dao.put(SettingEntity(KEY_EINK_CONTEXT_PROFILES, encodeEinkContextProfiles(next)))
    }

    override suspend fun setLastSeenVersion(version: String) =
        dao.put(SettingEntity(KEY_LAST_SEEN_VERSION, version))

    override suspend fun setActiveColorProfileId(id: Long) =
        dao.put(SettingEntity(KEY_ACTIVE_COLOR_PROFILE, id.toString()))

    override fun pluginConfig(pkg: String, key: String): Flow<String?> = dao.observe(pluginCfgKey(pkg, key))
    override suspend fun setPluginConfig(pkg: String, key: String, value: String) =
        dao.put(SettingEntity(pluginCfgKey(pkg, key), value))

    private companion object {
        fun pluginCfgKey(pkg: String, key: String) = "plugincfg:$pkg:$key"

        const val KEY_THEME = "theme_mode"
        const val KEY_LANG = "language"
        const val KEY_DISPLAY = "display_mode"
        const val KEY_SHELL_LAYOUT = "shell_layout_mode"
        const val KEY_BOOKMARK_MARKER_STYLE = "bookmark_marker_style"
        const val KEY_EXTERNAL_OPEN_BEHAVIOR = "external_open_behavior"
        const val KEY_DOWNLOAD_DIR = "download_dir"
        const val KEY_PANEL_OVERLAY = "guided_panel_overlay"
        const val KEY_USE_ML = "use_ml_detection"
        const val KEY_ACTIVE_COLOR_PROFILE = "active_color_profile_id"
        const val KEY_WEBTOON_OVERLAP = "webtoon_overlap_percent"
        const val KEY_LIBRARIES_VIEW_MODE = "libraries_view_mode"
        const val KEY_COLLECTIONS_VIEW_MODE = "collections_view_mode"
        const val KEY_CHAPTER_VIEW_MODE = "chapter_view_mode"
        const val KEY_NOVEL_FONT_SIZE = "novel_font_size_em"
        const val KEY_NOVEL_LINE_HEIGHT = "novel_line_height"
        const val KEY_NOVEL_MARGIN_PRESET = "novel_margin_preset"
        const val KEY_NOVEL_FONT_FAMILY = "novel_font_family"
        const val KEY_NOVEL_TEXT_ALIGN = "novel_text_align"
        const val KEY_NOVEL_HYPHENATION = "novel_hyphenation_lang"
        const val KEY_NOVEL_FONT_WEIGHT = "novel_font_weight"
        const val KEY_OFFICIAL_REPO_ENABLED = "official_repo_enabled"
        const val KEY_ACTIVE_UI_PACK = "active_ui_pack"
        const val KEY_LAST_SEEN_VERSION = "last_seen_version"
        const val KEY_EINK_CONTEXT_PROFILES = "eink_context_profiles"
        const val KEY_SCREENSAVER_MODE = "screensaver_mode"
        const val KEY_SCREENSAVER_CUSTOM_URI = "screensaver_custom_uri"
        const val KEY_SCREENSAVER_FILL_CROP = "screensaver_fill_crop"
    }
}
