package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.SubPageScaffold
import com.komgareader.app.ui.theme.ThemeMode

/** Darstellung: Theme-Auswahl (Hell/Dunkel/System). */
@Composable
fun AppearanceSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val themeModeStr by viewModel.themeMode.collectAsState()
    val themeMode = runCatching { ThemeMode.valueOf(themeModeStr) }.getOrDefault(ThemeMode.SYSTEM)

    SubPageScaffold(title = s.settingsAppearance, onBack = onBack) {
        Column {
            SectionHeader(s.settingsTheme)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> s.themeLight
                    ThemeMode.DARK -> s.themeDark
                    ThemeMode.SYSTEM -> s.themeSystem
                }
                ChoiceRow(label, selected = mode == themeMode) { viewModel.setTheme(mode.name) }
            }
        }
    }
}
