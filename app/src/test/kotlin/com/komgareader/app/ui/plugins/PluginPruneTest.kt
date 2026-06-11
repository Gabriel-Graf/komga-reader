package com.komgareader.app.ui.plugins

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import kotlin.test.Test
import kotlin.test.assertEquals

class PluginPruneTest {

    private fun pluginConfig(id: Long, pkg: String) = ServerConfig(
        name = "P", baseUrl = "", kind = SourceKind.PLUGIN,
        extras = mapOf("__pkg" to pkg, "__entry" to "E", "__sig" to "S"), id = id,
    )

    @Test
    fun dropsTaggedPresetPackagesThatAreNoLongerInstalled() {
        val plan = planPluginPrune(
            installedPackages = setOf("com.keep"),
            taggedPresetPackages = listOf("com.keep", "com.gone"),
            pluginSourceConfigs = emptyList(),
        )
        assertEquals(listOf("com.gone"), plan.presetPackagesToDrop)
        assertEquals(emptyList(), plan.sourceConfigIdsToRemove)
    }

    @Test
    fun removesPluginSourceConfigsWhoseApkVanished() {
        val plan = planPluginPrune(
            installedPackages = setOf("com.live"),
            taggedPresetPackages = emptyList(),
            pluginSourceConfigs = listOf(pluginConfig(1, "com.live"), pluginConfig(2, "com.dead")),
        )
        assertEquals(emptyList(), plan.presetPackagesToDrop)
        assertEquals(listOf(2L), plan.sourceConfigIdsToRemove)
    }

    @Test
    fun keepsEverythingWhenAllInstalled() {
        val plan = planPluginPrune(
            installedPackages = setOf("a", "b"),
            taggedPresetPackages = listOf("a"),
            pluginSourceConfigs = listOf(pluginConfig(1, "b")),
        )
        assertEquals(emptyList(), plan.presetPackagesToDrop)
        assertEquals(emptyList(), plan.sourceConfigIdsToRemove)
    }

    @Test
    fun deduplicatesPresetPackages() {
        val plan = planPluginPrune(
            installedPackages = emptySet(),
            taggedPresetPackages = listOf("com.gone", "com.gone"),
            pluginSourceConfigs = emptyList(),
        )
        assertEquals(listOf("com.gone"), plan.presetPackagesToDrop)
    }
}
