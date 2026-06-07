package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.SubPageScaffold
import com.komgareader.domain.model.DisplayMode

/** Reader: Anzeige-Modus (E-Ink vs. Smartphone) und Debug-Optionen. */
@Composable
fun ReaderSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val displayModeStr by viewModel.displayMode.collectAsState()
    val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
    val panelOverlay by viewModel.guidedPanelOverlay.collectAsState()

    SubPageScaffold(title = s.settingsReader, onBack = onBack) {
        Column {
            SectionHeader(s.settingsDisplayMode)
            Text(
                s.displayModeHelper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            DisplayMode.entries.forEach { dm ->
                val label = when (dm) {
                    DisplayMode.EINK -> s.displayEink
                    DisplayMode.SMARTPHONE -> s.displaySmartphone
                }
                ChoiceRow(label, selected = dm == displayMode) { viewModel.setDisplayMode(dm.name) }
            }

            SectionHeader(s.settingsGuidedDebug)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(s.readerPanelOverlay, modifier = Modifier.weight(1f))
                Switch(
                    checked = panelOverlay,
                    onCheckedChange = { viewModel.setGuidedPanelOverlay(it) },
                )
            }
        }
    }
}
