package com.komgareader.data.plugin.repo

/** Typ-Filter des Plugins-Tabs (Chip „Alle/Quellen/Presets/Sprachen/Reader-Presets"). */
enum class PluginTypeFilter { ALL, SOURCES, PRESETS, LANGUAGES, READER_PRESETS }

/** Eine Browser-Zeile: gemergter Eintrag + abgeleiteter Install-Zustand + ABI-Kompatibilität. */
data class BrowserRow(
    val item: BrowsableEntry,
    val state: InstallState,
    val compatible: Boolean,
)

/** Quellen-agnostische Sicht auf ein installiertes Plugin-APK (Quelle ODER Preset). */
data class InstalledEntry(
    val packageName: String,
    val displayName: String,
    val kind: PluginKind,
)

/** Ergebnis der Filterung: beide Abschnitte + ob ein Divider dazwischen gehört. */
data class VisibleRows(
    val installed: List<InstalledEntry>,
    val discovered: List<BrowserRow>,
    val showDivider: Boolean,
)

private fun PluginKind.matches(filter: PluginTypeFilter): Boolean = when (filter) {
    PluginTypeFilter.ALL -> true
    PluginTypeFilter.SOURCES -> this == PluginKind.SOURCE
    PluginTypeFilter.PRESETS -> this == PluginKind.PRESET
    PluginTypeFilter.LANGUAGES -> this == PluginKind.LANGUAGE
    PluginTypeFilter.READER_PRESETS -> this == PluginKind.READER_PRESET
}

/**
 * Reine Filterung des Plugins-Tabs: Typ-Chip + Suchtext auf installierte UND entdeckte anwenden.
 * Installierte bleiben oben; der Divider erscheint nur, wenn nach Filterung BEIDE Abschnitte Inhalt
 * haben (keine schwebende Linie über leerem Bereich).
 */
fun visibleRows(
    installed: List<InstalledEntry>,
    discovered: List<BrowserRow>,
    query: String,
    typeFilter: PluginTypeFilter,
): VisibleRows {
    val q = query.trim()
    val filteredInstalled = installed.filter {
        it.kind.matches(typeFilter) && (q.isBlank() || it.displayName.contains(q, ignoreCase = true))
    }
    val filteredDiscovered = discovered.filter {
        it.item.kind.matches(typeFilter) && (
            q.isBlank() ||
                it.item.entry.name.contains(q, ignoreCase = true) ||
                it.item.entry.description.contains(q, ignoreCase = true)
        )
    }
    return VisibleRows(filteredInstalled, filteredDiscovered, showDivider = filteredInstalled.isNotEmpty() && filteredDiscovered.isNotEmpty())
}
