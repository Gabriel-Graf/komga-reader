package com.komgareader.app.data

import com.komgareader.domain.model.SourceKind
import com.komgareader.plugin.ConfigSchema
import com.komgareader.plugin.PluginMetadata
import com.komgareader.plugin.host.DiscoveredPlugin
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginServerConfigTest {

    private val plugin = DiscoveredPlugin(
        packageName = "com.komgareader.plugin.suwayomi",
        signatureSha256 = "abcd",
        abiVersion = 1,
        metadata = PluginMetadata(displayName = "Suwayomi"),
        configSchema = ConfigSchema(emptyList()),
        entryClass = "com.komgareader.plugin.suwayomi.SuwayomiSourcePlugin",
    )

    @Test
    fun new_connection_defaults_to_id_zero() {
        val cfg = pluginServerConfig(plugin, mapOf("url" to "http://host:4567"))

        assertEquals(0L, cfg.id)
        assertEquals(SourceKind.PLUGIN, cfg.kind)
        assertEquals("http://host:4567", cfg.baseUrl)
        assertEquals("http://host:4567", cfg.extras["url"])
        assertEquals("com.komgareader.plugin.suwayomi", cfg.extras["__pkg"])
    }

    @Test
    fun edit_passes_existing_id_for_in_place_update() {
        // Editing a plugin server must reuse the existing rowid so save() upserts in place
        // (a fresh id would create a duplicate connection instead of updating the URL).
        val cfg = pluginServerConfig(plugin, mapOf("url" to "http://192.168.1.10:4567"), id = 42L)

        assertEquals(42L, cfg.id)
        assertEquals("http://192.168.1.10:4567", cfg.baseUrl)
        assertEquals("http://192.168.1.10:4567", cfg.extras["url"])
    }
}
