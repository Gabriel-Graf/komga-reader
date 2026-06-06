package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
    language: Language,
    onLanguageChange: (Language) -> Unit,
    onBack: () -> Unit,
) {
    val s = LocalStrings.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(s.settingsTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(16.dp)) {
            Text(s.settingsTheme)
            ThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    ThemeMode.LIGHT -> s.themeLight
                    ThemeMode.DARK -> s.themeDark
                    ThemeMode.SYSTEM -> s.themeSystem
                }
                OptionRow(label, selected = mode == themeMode) { onThemeChange(mode) }
            }
            Text(s.settingsLanguage, modifier = Modifier.padding(top = 16.dp))
            Language.entries.forEach { lang ->
                OptionRow(lang.code.uppercase(), selected = lang == language) { onLanguageChange(lang) }
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, onClick = onSelect).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}
