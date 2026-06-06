package com.komgareader.app.ui.settings

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChromeReaderMode
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.BuildConfig
import com.komgareader.app.i18n.Language as AppLanguage
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.i18n.Strings
import com.komgareader.app.ui.components.SettingsTile
import com.komgareader.app.ui.theme.EinkTokens
import com.komgareader.app.ui.theme.ThemeMode
import com.komgareader.domain.model.DisplayMode

/** Die einzelnen Settings-Unterseiten — Routing in MainActivity. */
enum class SettingsPage { CONNECTION, APPEARANCE, COLOR_FILTER, READER, DOWNLOADS, LANGUAGE, ABOUT }

private data class SettingsTileModel(
    val page: SettingsPage,
    val icon: ImageVector,
    val title: String,
    val summary: String,
    /** Durchsuchbarer Text: Titel + Summary + Hilfe-/Info-Texte der Unterseite. */
    val keywords: String,
)

/**
 * Einstellungs-Landing im Onyx-Speicher-Stil: adaptives Kachelraster. Inhalt-only —
 * TopBar/Suche kommen aus HomeScreen. [query] filtert Kacheln über Name + Hilfetexte.
 */
@Composable
fun SettingsLandingScreen(
    onOpenPage: (SettingsPage) -> Unit,
    query: String = "",
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val server by viewModel.server.collectAsState()
    val themeModeStr by viewModel.themeMode.collectAsState()
    val displayModeStr by viewModel.displayMode.collectAsState()
    val downloadDir by viewModel.downloadDir.collectAsState()
    val languageStr by viewModel.language.collectAsState()

    val themeLabel = when (runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)) {
        ThemeMode.LIGHT -> s.themeLight
        ThemeMode.DARK -> s.themeDark
        ThemeMode.SYSTEM -> s.themeSystem
    }
    val displayLabel = when (runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)) {
        DisplayMode.EINK -> s.displayEink
        DisplayMode.SMARTPHONE -> s.displaySmartphone
    }
    val folderLabel = downloadDir?.let { dir ->
        runCatching { Uri.parse(dir).lastPathSegment ?: dir }.getOrElse { dir }
    } ?: s.defaultFolder
    val languageLabel = if (languageStr == AppLanguage.EN.code) "English" else "Deutsch"

    val tiles = buildTiles(s, server?.name, themeLabel, displayLabel, folderLabel, languageLabel)
    val shown = if (query.isBlank()) tiles
    else tiles.filter { it.keywords.contains(query, ignoreCase = true) }

    if (shown.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(s.searchNoResults, textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp))
        }
        return
    }

    LazyVerticalGrid(
        // Adaptiv: schmale Screens (Smartphone) → eine Spalte, breite (E-Ink-Tablet) → mehrere.
        columns = GridCells.Adaptive(minSize = 320.dp),
        modifier = modifier.fillMaxSize().padding(EinkTokens.screenPadding),
        horizontalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
        verticalArrangement = Arrangement.spacedBy(EinkTokens.tileGap),
    ) {
        items(shown, key = { it.page }) { tile ->
            SettingsTile(tile.icon, tile.title, tile.summary, { onOpenPage(tile.page) }, showChevron = false)
        }
    }
}

private fun buildTiles(
    s: Strings,
    serverName: String?,
    themeLabel: String,
    displayLabel: String,
    folderLabel: String,
    languageLabel: String,
): List<SettingsTileModel> = listOf(
    SettingsTileModel(
        SettingsPage.CONNECTION, Icons.Outlined.Cloud, s.settingsConnection, serverName ?: s.notConnected,
        "${s.settingsConnection} ${s.settingsServer} ${s.serverUrl} ${s.serverUrlHelper} " +
            "${s.serverApiKeyOptional} ${s.serverUsername} ${s.serverPassword} ${s.connect} ${s.disconnect} ${serverName ?: ""}",
    ),
    SettingsTileModel(
        SettingsPage.APPEARANCE, Icons.Outlined.Contrast, s.settingsAppearance, themeLabel,
        "${s.settingsAppearance} ${s.settingsTheme} ${s.themeLight} ${s.themeDark} ${s.themeSystem}",
    ),
    SettingsTileModel(
        SettingsPage.COLOR_FILTER, Icons.Outlined.Palette, s.settingsColorFilter, s.colorFilterSummary,
        "${s.settingsColorFilter} ${s.colorFilterProfiles} ${s.colorFilterSaturation} ${s.colorFilterContrast} ${s.colorFilterBrightness}",
    ),
    SettingsTileModel(
        SettingsPage.READER, Icons.Outlined.ChromeReaderMode, s.settingsReader, displayLabel,
        "${s.settingsReader} ${s.settingsDisplayMode} ${s.displayModeHelper} ${s.displayEink} ${s.displaySmartphone}",
    ),
    SettingsTileModel(
        SettingsPage.DOWNLOADS, Icons.Outlined.Download, s.settingsDownloads, folderLabel,
        "${s.settingsDownloads} ${s.downloadFolder} ${s.chooseFolder} ${s.defaultFolder}",
    ),
    SettingsTileModel(
        SettingsPage.LANGUAGE, Icons.Outlined.Language, s.settingsLanguage, languageLabel,
        "${s.settingsLanguage} Deutsch English",
    ),
    SettingsTileModel(
        SettingsPage.ABOUT, Icons.Outlined.Info, s.settingsAbout, "v${BuildConfig.VERSION_NAME}",
        "${s.settingsAbout} ${s.appName} ${s.aboutDevice} ${s.versionLabel} ${BuildConfig.VERSION_NAME}",
    ),
)
