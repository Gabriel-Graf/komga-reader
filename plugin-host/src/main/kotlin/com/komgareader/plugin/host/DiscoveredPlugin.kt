package com.komgareader.plugin.host

import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.PluginMetadata

/** Ein installiertes, ABI-kompatibles Quellen-Plugin (für die Settings-Auswahlliste). */
data class DiscoveredPlugin(
    val packageName: String,
    /** SHA-256-Hex der Signing-Cert-Bytes — wird bei der Erst-Bestätigung (TOFU) angezeigt
     *  und anschließend als Pin gespeichert. */
    val signatureSha256: String,
    val abiVersion: Int,
    val metadata: PluginMetadata,
    val configSchema: ConfigSchema,
)
