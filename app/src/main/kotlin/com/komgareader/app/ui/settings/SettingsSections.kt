package com.komgareader.app.ui.settings

import androidx.compose.runtime.Composable
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Strings
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.SettingsSection
import com.komgareader.ui.slots.SettingsSectionId

/**
 * Baut die Sektionsliste (Reihenfolge = UI-Reihenfolge). [searchTerms] sind lokalisierte
 * Strings (Titel + Zeilen-Labels + Helper) — Grundlage für Filter + „warum gefunden".
 */
@Composable
fun buildSettingsSections(
    s: Strings,
    viewModel: SettingsViewModel,
    aboutBadge: Boolean = false,
): List<SettingsSection> = listOf(
    SettingsSection(
        id = SettingsSectionId.CONNECTION,
        icon = AppIcons.Connection,
        title = s.settingsConnection,
        searchTerms = listOf(
            s.settingsConnection, s.settingsServer, s.serverDisplayName, s.serverUrl, s.serverUrlHelper,
            s.serverApiKeyOptional, s.serverUsername, s.serverPassword, s.connect, s.disconnect,
            s.connectedServers, s.addServer,
        ),
        content = { q -> ConnectionSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.APPEARANCE,
        icon = AppIcons.Contrast,
        title = s.settingsAppearance,
        searchTerms = listOf(
            s.settingsAppearance,
            s.settingsDisplayMode, s.displayModeHelper, s.displayEink, s.displaySmartphone,
            s.settingsTheme, s.themeLight, s.themeDark, s.themeSystem,
            s.settingsUiPack, s.uiPackDefault,
        ),
        content = { q -> AppearanceSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.COLOR_FILTER,
        icon = AppIcons.Palette,
        title = s.settingsColorFilter,
        searchTerms = listOf(
            s.settingsColorFilter, s.colorFilterSummary, s.colorFilterProfiles,
            s.colorFilterSaturation, s.colorFilterContrast, s.colorFilterBrightness,
        ),
        // Verwaltet eigenes Scrollen: pinnt das Vorschau-Cover, scrollt nur die Regler.
        scrollable = false,
        content = { q -> ColorFilterSettingsContent(q) },
    ),
    SettingsSection(
        id = SettingsSectionId.READER,
        icon = AppIcons.Reader,
        title = s.settingsReader,
        searchTerms = listOf(
            s.settingsReader,
            s.settingsScopeGeneral, s.settingsScopeNovel, s.settingsScopeWebtoon, s.settingsScopeComic,
            s.settingsWebtoon, s.webtoonOverlap, s.webtoonOverlapHelper,
            s.settingsShellLayout, s.shellLayoutAuto, s.shellLayoutCompact, s.shellLayoutExpanded,
            s.settingsEinkRefresh, s.deviceManagedRefresh, s.deviceManagedRefreshHelper,
            s.novelTypography, s.novelTextHeading, s.novelFontSize, s.novelLineHeight, s.novelFontWeight,
            s.novelMargin, s.novelTextAlign, s.novelHyphenation, s.novelFontFamily,
            s.readerPanelOverlay,
        ),
        content = { q -> ReaderSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.DOWNLOADS,
        icon = AppIcons.Download,
        title = s.settingsDownloads,
        searchTerms = listOf(s.settingsDownloads, s.downloadFolder, s.chooseFolder, s.resetFolder, s.defaultFolder),
        content = { q -> DownloadsSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.LANGUAGE,
        icon = AppIcons.Language,
        title = s.settingsLanguage,
        searchTerms = listOf(s.settingsLanguage, "Deutsch", "English"),
        content = { q -> LanguageSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.ABOUT,
        icon = AppIcons.Info,
        title = s.settingsAbout,
        searchTerms = listOf(
            s.settingsAbout, s.appName, s.aboutDevice, s.versionLabel, BuildConfig.VERSION_NAME,
            s.aboutCheckUpdates, s.aboutSourceCode,
        ),
        badge = aboutBadge,
        content = { q -> AboutContent(q) },
    ),
)
