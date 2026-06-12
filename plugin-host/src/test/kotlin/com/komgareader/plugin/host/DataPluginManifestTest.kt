package com.komgareader.plugin.host

import com.komgareader.plugin.PluginCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DataPluginManifestTest {

    @Test fun resolvesExplicitCategoryAndAsset() {
        val r = resolveDataPluginManifest(
            dataCategory = "LANGUAGE", dataAsset = "lang_es.json", legacyColorPresets = null,
        )
        assertEquals(PluginCategory.LANGUAGE to "lang_es.json", r)
    }

    @Test fun legacyColorPresetsKeyMapsToColorPresetCategory() {
        val r = resolveDataPluginManifest(
            dataCategory = null, dataAsset = null, legacyColorPresets = "presets.json",
        )
        assertEquals(PluginCategory.COLOR_PRESET to "presets.json", r)
    }

    @Test fun explicitCategoryWinsOverLegacy() {
        val r = resolveDataPluginManifest(
            dataCategory = "READER_PRESET", dataAsset = "rp.json", legacyColorPresets = "old.json",
        )
        assertEquals(PluginCategory.READER_PRESET to "rp.json", r)
    }

    @Test fun nullWhenNoKeysPresent() {
        assertNull(resolveDataPluginManifest(null, null, null))
    }

    @Test fun nullWhenCategoryWithoutAsset() {
        assertNull(resolveDataPluginManifest("LANGUAGE", null, null))
    }

    @Test fun nullWhenUnknownCategory() {
        assertNull(resolveDataPluginManifest("BOGUS", "x.json", null))
    }

    @Test fun blankCategoryFallsThroughToLegacy() {
        val r = resolveDataPluginManifest(
            dataCategory = "  ", dataAsset = "x.json", legacyColorPresets = "old.json",
        )
        assertEquals(PluginCategory.COLOR_PRESET to "old.json", r)
    }
}
