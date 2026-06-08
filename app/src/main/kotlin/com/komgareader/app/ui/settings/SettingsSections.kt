package com.komgareader.app.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Strings
import com.komgareader.app.ui.icons.AppIcons

/** Die einzelnen Settings-Sektionen. Reihenfolge = Sidebar-/Accordion-Reihenfolge. */
enum class SettingsSectionId { CONNECTION, APPEARANCE, COLOR_FILTER, READER, DOWNLOADS, LANGUAGE, ABOUT }

/**
 * Eine Settings-Sektion: Metadaten + such-Terme + ihr Inhalt.
 *
 * @param scrollable false, wenn die Sektion ihr eigenes Scrollen verwaltet (z. B. Farbfilter
 *   pinnt die Vorschau und scrollt nur die Regler) — der Host gibt dann eine gebundene Höhe
 *   statt selbst zu scrollen.
 */
data class SettingsSection(
    val id: SettingsSectionId,
    val icon: ImageVector,
    val title: String,
    val searchTerms: List<String>,
    val scrollable: Boolean = true,
    val content: @Composable (query: String) -> Unit,
)

/**
 * Baut die Sektionsliste (Reihenfolge = UI-Reihenfolge). [searchTerms] sind lokalisierte
 * Strings (Titel + Zeilen-Labels + Helper) — Grundlage für Filter + „warum gefunden".
 */
@Composable
fun buildSettingsSections(s: Strings, viewModel: SettingsViewModel): List<SettingsSection> = listOf(
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
        searchTerms = listOf(s.settingsAppearance, s.settingsTheme, s.themeLight, s.themeDark, s.themeSystem),
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
            s.settingsReader, s.settingsWebtoon, s.webtoonOverlap, s.webtoonOverlapHelper,
            s.settingsDisplayMode, s.displayModeHelper, s.displayEink, s.displaySmartphone,
            s.settingsEinkRefresh, s.deviceManagedRefresh, s.deviceManagedRefreshHelper,
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
        searchTerms = listOf(s.settingsAbout, s.appName, s.aboutDevice, s.versionLabel, BuildConfig.VERSION_NAME),
        content = { q -> AboutContent(q) },
    ),
)
