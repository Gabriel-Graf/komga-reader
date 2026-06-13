package com.komgareader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Strings
import com.komgareader.ui.icons.AppIcons
import com.komgareader.ui.slots.SettingsSection
import com.komgareader.ui.slots.SettingsSectionId

/**
 * Builds the section list (order = UI order). [searchTerms] are localised strings
 * (title + row labels + helpers) — basis for filter + "why found".
 *
 * The "E-Ink Dynamics" section is only included when the device advertises at least one
 * refresh mode (i.e. on Onyx Boox hardware). On non-Boox devices the section is absent.
 */
@Composable
fun buildSettingsSections(s: Strings, viewModel: SettingsViewModel): List<SettingsSection> {
    val displayMode by viewModel.displayMode.collectAsState()
    return buildList {
        add(
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
        )
        add(
            SettingsSection(
                id = SettingsSectionId.APPEARANCE,
                icon = AppIcons.Contrast,
                title = s.settingsAppearance,
                searchTerms = listOf(s.settingsAppearance, s.settingsTheme, s.themeLight, s.themeDark, s.themeSystem),
                content = { q -> AppearanceSettingsContent(viewModel, q) },
            ),
        )
        add(
            SettingsSection(
                id = SettingsSectionId.COLOR_FILTER,
                icon = AppIcons.Palette,
                title = s.settingsColorFilter,
                searchTerms = listOf(
                    s.settingsColorFilter, s.colorFilterSummary, s.colorFilterProfiles,
                    s.colorFilterSaturation, s.colorFilterContrast, s.colorFilterBrightness,
                ),
                // Manages its own scrolling: pins the preview cover, scrolls only the sliders.
                scrollable = false,
                content = { q -> ColorFilterSettingsContent(q) },
            ),
        )
        add(
            SettingsSection(
                id = SettingsSectionId.READER,
                icon = AppIcons.Reader,
                title = s.settingsReader,
                searchTerms = listOf(
                    s.settingsReader,
                    s.settingsScopeGeneral, s.settingsScopeNovel, s.settingsScopeWebtoon, s.settingsScopeComic,
                    s.settingsWebtoon, s.webtoonOverlap, s.webtoonOverlapHelper,
                    s.settingsDisplayMode, s.displayModeHelper, s.displayEink, s.displaySmartphone,
                    s.settingsShellLayout, s.shellLayoutAuto, s.shellLayoutCompact, s.shellLayoutExpanded,
                    s.novelTypography, s.novelTextHeading, s.novelFontSize, s.novelLineHeight, s.novelFontWeight,
                    s.novelMargin, s.novelTextAlign, s.novelHyphenation, s.novelFontFamily,
                    s.readerPanelOverlay,
                ),
                content = { q -> ReaderSettingsContent(viewModel, q) },
            ),
        )
        // Only shown on Boox hardware (at least one refresh mode advertised) AND in E-Ink mode
        // (the section has no effect in Smartphone display mode).
        if (viewModel.einkRefreshModes.isNotEmpty() && displayMode != "SMARTPHONE") {
            add(
                SettingsSection(
                    id = SettingsSectionId.EINK_DYNAMICS,
                    icon = AppIcons.Refresh,
                    title = s.settingsEinkDynamics,
                    searchTerms = listOf(
                        s.settingsEinkDynamics, s.settingsEinkDynamicsDesc,
                        s.einkAxisRefresh, s.einkAxisColor,
                        s.einkContextHome, s.einkContextPaged, s.einkContextWebtoon,
                        s.einkContextComic, s.einkContextNovel,
                        s.einkModeDeviceDefault,
                        "eink", "refresh", "farbe", "modus", "color", "mode",
                    ),
                    content = { EinkDynamicsSettingsContent(viewModel) },
                ),
            )
        }
        add(
            SettingsSection(
                id = SettingsSectionId.DOWNLOADS,
                icon = AppIcons.Download,
                title = s.settingsDownloads,
                searchTerms = listOf(s.settingsDownloads, s.downloadFolder, s.chooseFolder, s.resetFolder, s.defaultFolder),
                content = { q -> DownloadsSettingsContent(viewModel, q) },
            ),
        )
        add(
            SettingsSection(
                id = SettingsSectionId.LANGUAGE,
                icon = AppIcons.Language,
                title = s.settingsLanguage,
                searchTerms = listOf(s.settingsLanguage, "Deutsch", "English"),
                content = { q -> LanguageSettingsContent(viewModel, q) },
            ),
        )
        add(
            SettingsSection(
                id = SettingsSectionId.ABOUT,
                icon = AppIcons.Info,
                title = s.settingsAbout,
                searchTerms = listOf(s.settingsAbout, s.appName, s.aboutDevice, s.versionLabel, BuildConfig.VERSION_NAME),
                content = { q -> AboutContent(q) },
            ),
        )
    }
}
