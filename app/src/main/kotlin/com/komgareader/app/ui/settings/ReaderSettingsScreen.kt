package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.ChoiceRow
import com.komgareader.app.ui.components.SectionHeader
import com.komgareader.app.ui.components.StepperRow
import com.komgareader.app.ui.components.SubPageScaffold
import com.komgareader.domain.model.DisplayMode

/** Schrittweite und Grenzen der Webtoon-Überlappung (in Prozent). */
private const val OVERLAP_STEP = 5
private const val OVERLAP_MIN = 0
private const val OVERLAP_MAX = 50

/** Reader-Einstellungen: Webtoon (Frame-Überlappung) + Anzeige-Modus (E-Ink/Smartphone). */
@Composable
fun ReaderSettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val s = LocalStrings.current
    val displayModeStr by viewModel.displayMode.collectAsState()
    val displayMode = runCatching { DisplayMode.valueOf(displayModeStr) }.getOrDefault(DisplayMode.EINK)
    val overlap by viewModel.webtoonOverlapPercent.collectAsState()

    SubPageScaffold(title = s.settingsReader, onBack = onBack) {
        Column {
            SectionHeader(s.settingsWebtoon)
            Text(
                s.webtoonOverlapHelper,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            StepperRow(
                label = s.webtoonOverlap,
                value = overlap,
                canDecrement = overlap > OVERLAP_MIN,
                canIncrement = overlap < OVERLAP_MAX,
                onDecrement = { viewModel.setWebtoonOverlap(overlap - OVERLAP_STEP) },
                onIncrement = { viewModel.setWebtoonOverlap(overlap + OVERLAP_STEP) },
                display = { "$it %" },
            )
        }

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
        }
    }
}
