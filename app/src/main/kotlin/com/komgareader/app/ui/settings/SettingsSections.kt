package com.komgareader.app.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Strings

/** Eine Settings-Sektion: Metadaten + such-Terme + ihr Inhalt. */
data class SettingsSection(
    val id: SettingsSectionId,
    val icon: ImageVector,
    val title: String,
    val searchTerms: List<String>,
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
        icon = Icons.Outlined.Cloud,
        title = s.settingsConnection,
        searchTerms = listOf(
            s.settingsConnection, s.settingsServer, s.serverDisplayName, s.serverUrl, s.serverUrlHelper,
            s.serverApiKeyOptional, s.serverUsername, s.serverPassword, s.connect, s.disconnect,
        ),
        content = { q -> ConnectionSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.APPEARANCE,
        icon = Icons.Outlined.Contrast,
        title = s.settingsAppearance,
        searchTerms = listOf(s.settingsAppearance, s.settingsTheme, s.themeLight, s.themeDark, s.themeSystem),
        content = { q -> AppearanceSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.READER,
        icon = Icons.Outlined.ChromeReaderMode,
        title = s.settingsReader,
        searchTerms = listOf(
            s.settingsReader, s.settingsWebtoon, s.webtoonOverlap, s.webtoonOverlapHelper,
            s.settingsDisplayMode, s.displayModeHelper, s.displayEink, s.displaySmartphone,
        ),
        content = { q -> ReaderSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.DOWNLOADS,
        icon = Icons.Outlined.Download,
        title = s.settingsDownloads,
        searchTerms = listOf(s.settingsDownloads, s.downloadFolder, s.chooseFolder, s.resetFolder, s.defaultFolder),
        content = { q -> DownloadsSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.LANGUAGE,
        icon = Icons.Outlined.Language,
        title = s.settingsLanguage,
        searchTerms = listOf(s.settingsLanguage, "Deutsch", "English"),
        content = { q -> LanguageSettingsContent(viewModel, q) },
    ),
    SettingsSection(
        id = SettingsSectionId.ABOUT,
        icon = Icons.Outlined.Info,
        title = s.settingsAbout,
        searchTerms = listOf(s.settingsAbout, s.appName, s.aboutDevice, s.versionLabel, BuildConfig.VERSION_NAME),
        content = { q -> AboutContent(q) },
    ),
)
