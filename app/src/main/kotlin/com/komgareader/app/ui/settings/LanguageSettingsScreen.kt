package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.Language
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.SubPageScaffold

/** Sprache: DE/EN. Anzeigenamen jeweils in der eigenen Sprache. */
@Composable
fun LanguageSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val languageStr by viewModel.language.collectAsState()

    SubPageScaffold(title = s.settingsLanguage, onBack = onBack) {
        Column {
            Language.entries.forEach { lang ->
                val label = when (lang) {
                    Language.DE -> "Deutsch"
                    Language.EN -> "English"
                }
                ChoiceRow(label, selected = lang.code == languageStr) { viewModel.setLanguage(lang.code) }
            }
        }
    }
}
