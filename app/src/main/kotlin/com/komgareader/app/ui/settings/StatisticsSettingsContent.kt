package com.komgareader.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.app.ui.components.SettingsGroup
import com.komgareader.domain.model.ReaderKind
import com.komgareader.ui.theme.EinkTokens

/**
 * Read-only statistics section: total reading time, per-reader-kind breakdown,
 * started and finished works. No interaction — purely observes [SettingsViewModel.statsState].
 */
@Composable
fun StatisticsSettingsContent(viewModel: SettingsViewModel) {
    val s = LocalStrings.current
    val stats by viewModel.statsState.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(EinkTokens.sectionGap)) {
        if (stats.totalMs == 0L) {
            SettingsGroup(s.statsTitle, query = "") {
                Text(
                    text = s.statsEmpty,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            // Total reading time
            SettingsGroup(s.statsTotalTime, query = "") {
                val totalMin = (stats.totalMs / 60_000L).toInt()
                StatRow(label = s.statsTotalTime, value = s.statsDuration(totalMin / 60, totalMin % 60))
            }

            // Per-reader breakdown
            SettingsGroup(s.statsPerReader, query = "") {
                val kindLabels = mapOf(
                    ReaderKind.PAGED to s.statsReaderPaged,
                    ReaderKind.WEBTOON to s.statsReaderWebtoon,
                    ReaderKind.COMIC to s.statsReaderComic,
                    ReaderKind.NOVEL to s.statsReaderNovel,
                )
                ReaderKind.entries.forEach { kind ->
                    val ms = stats.perKindMs[kind] ?: 0L
                    if (ms > 0L) {
                        val min = (ms / 60_000L).toInt()
                        StatRow(
                            label = kindLabels[kind] ?: kind.name,
                            value = s.statsDuration(min / 60, min % 60),
                        )
                    }
                }
            }

            // Work counts
            SettingsGroup(s.statsStarted, query = "") {
                StatRow(label = s.statsStarted, value = stats.startedWorks.toString())
                StatRow(label = s.statsFinished, value = stats.finishedWorks.toString())
            }
        }
    }
}

/** Label left (muted) · value right — matches the read-only AboutRow style. */
@Composable
private fun StatRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
