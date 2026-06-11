package com.komgareader.app.ui.plugins

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig

/** Was beim Re-Scan aufgeräumt werden muss, weil das zugehörige Plugin-APK deinstalliert wurde. */
data class PrunePlan(
    val presetPackagesToDrop: List<String>,
    val sourceConfigIdsToRemove: List<Long>,
)

/**
 * Reiner Cleanup-Planer für deinstallierte Plugin-APKs (Uninstall liefert kein verlässliches
 * Callback → Re-Scan beim Tab-`onResume`). [installedPackages] = aktuell installierte, ABI-
 * kompatible Plugin-Pakete (Quellen ∪ Presets). Alles, was in der DB getaggt/konfiguriert ist,
 * aber nicht mehr installiert, wird zum Aufräumen markiert.
 */
fun planPluginPrune(
    installedPackages: Set<String>,
    taggedPresetPackages: List<String>,
    pluginSourceConfigs: List<ServerConfig>,
): PrunePlan {
    val presetDrop = taggedPresetPackages.distinct().filter { it !in installedPackages }
    val sourceRemove = pluginSourceConfigs
        .filter { it.kind == SourceKind.PLUGIN }
        .filter { (it.extras["__pkg"] ?: return@filter false) !in installedPackages }
        .map { it.id }
    return PrunePlan(presetDrop, sourceRemove)
}
