package com.komgareader.app.ui.components

import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.IntOffset
import com.komgareader.app.i18n.LocalStrings
import com.komgareader.data.plugin.repo.PluginTypeFilter

/**
 * Filter-Menü des Plugins-Tabs: Einfach-Auswahl Alle/Quellen/Presets/Sprachen/Reader-Presets
 * (Häkchen auf aktiv). Spiegelt [TypeFilterMenu] (geteiltes [FilterRow] + [AnchoredMenuPopup]);
 * klappt unter dem Filter-Icon auf. Auswahl schließt das Menü (Einfach-Auswahl, kein Mehrfach-Toggle).
 */
@Composable
fun PluginFilterMenu(
    anchor: IntOffset,
    selected: PluginTypeFilter,
    onSelect: (PluginTypeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AnchoredMenuPopup(anchor = anchor, alignEnd = true, onDismiss = onDismiss) {
        FilterRow(label = s.pluginFilterAll, checked = selected == PluginTypeFilter.ALL) {
            onSelect(PluginTypeFilter.ALL); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterSources, checked = selected == PluginTypeFilter.SOURCES) {
            onSelect(PluginTypeFilter.SOURCES); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterPresets, checked = selected == PluginTypeFilter.PRESETS) {
            onSelect(PluginTypeFilter.PRESETS); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterLanguages, checked = selected == PluginTypeFilter.LANGUAGES) {
            onSelect(PluginTypeFilter.LANGUAGES); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterReaderPresets, checked = selected == PluginTypeFilter.READER_PRESETS) {
            onSelect(PluginTypeFilter.READER_PRESETS); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterUiPacks, checked = selected == PluginTypeFilter.UI_PACKS) {
            onSelect(PluginTypeFilter.UI_PACKS); onDismiss()
        }
        HorizontalDivider()
        FilterRow(label = s.pluginFilterFonts, checked = selected == PluginTypeFilter.FONTS) {
            onSelect(PluginTypeFilter.FONTS); onDismiss()
        }
    }
}
