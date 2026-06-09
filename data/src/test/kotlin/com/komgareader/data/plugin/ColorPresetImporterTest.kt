package com.komgareader.data.plugin

import com.komgareader.domain.model.ColorProfile
import com.komgareader.plugin.ColorPresetSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ColorPresetImporterTest {

    @Test
    fun out_of_range_values_are_clamped() {
        val spec = ColorPresetSpec(abiVersion = 1, name = "Wild", saturation = 9f, contrast = -3f, brightness = 2f)
        val profile = ColorPresetImporter.toProfile(spec)
        assertTrue(profile.saturation <= ColorProfile.SATURATION_MAX, "saturation clamped to MAX")
        assertTrue(profile.contrast >= ColorProfile.CONTRAST_MIN, "contrast clamped to MIN")
        assertFalse(profile.builtIn, "imported preset must not be builtIn")
    }

    @Test
    fun incompatible_abi_is_rejected() {
        val spec = ColorPresetSpec(abiVersion = 999, name = "Future", saturation = 1f, contrast = 1f, brightness = 1f)
        assertNull(ColorPresetImporter.toProfileOrNull(spec))
    }

    @Test
    fun compatible_abi_is_accepted() {
        val spec = ColorPresetSpec(abiVersion = 1, name = "Normal", saturation = 1.2f, contrast = 1.1f, brightness = 0.05f)
        val profile = ColorPresetImporter.toProfileOrNull(spec)
        assertNotNull(profile)
        assertFalse(profile.builtIn)
    }

    @Test
    fun in_range_values_are_not_changed() {
        val spec = ColorPresetSpec(abiVersion = 1, name = "Exact", saturation = 1.4f, contrast = 1.15f, brightness = 0.05f)
        val profile = ColorPresetImporter.toProfile(spec)
        assertEquals(1.4f, profile.saturation, 0.0001f)
        assertEquals(1.15f, profile.contrast, 0.0001f)
        assertEquals(0.05f, profile.brightness, 0.0001f)
    }
}
