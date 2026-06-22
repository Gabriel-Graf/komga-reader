package com.komgareader.app.data

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.plugin.host.DiscoveredPlugin

/**
 * Reine Hilfsfunktion: baut eine [ServerConfig] für eine bestätigte Plugin-Quelle.
 * Kapselt die Wiring-Keys `__pkg`/`__entry`/`__sig` (TOFU-Pin) an einer Stelle —
 * genutzt von [PluginsViewModel] und [SettingsViewModel] (DRY, keine Duplikation).
 *
 * [id] == 0 legt eine neue Verbindung an; ein bestehender Rowid lässt `save()` die Plugin-Quelle
 * **in-place aktualisieren** (Bearbeiten der URL/Config), statt einen zweiten Eintrag anzulegen.
 */
fun pluginServerConfig(
    plugin: DiscoveredPlugin,
    values: Map<String, String>,
    id: Long = 0L,
): ServerConfig =
    ServerConfig(
        name = plugin.metadata.displayName,
        baseUrl = values["url"]?.trim() ?: "",
        kind = SourceKind.PLUGIN,
        extras = values + mapOf(
            "__pkg" to plugin.packageName,
            "__entry" to plugin.entryClass,
            "__sig" to plugin.signatureSha256,
        ),
        id = id,
    )
