package com.komgareader.data.plugin

import com.komgareader.domain.model.ColorProfile
import com.komgareader.plugin.ColorPresetSpec
import com.komgareader.plugin.PluginAbi

/**
 * Überführt einen [ColorPresetSpec] (Plugin-Typ c, deklarative Daten) in ein [ColorProfile].
 * Liegt in :data, weil es sowohl :plugin-api (Spec) als auch :domain (ColorProfile) kennen muss.
 *
 * ABI-Kompatibilität: [toProfileOrNull] gibt null zurück, wenn die Spec außerhalb des
 * unterstützten ABI-Bereichs liegt. [toProfile] vertraut auf eine kompatible Spec.
 */
object ColorPresetImporter {

    /**
     * Gibt null zurück, wenn [spec.abiVersion] außerhalb [PluginAbi.MIN_SUPPORTED]..[PluginAbi.VERSION].
     * Sonst: geclampt auf die ColorProfile-Wertebereiche, builtIn = false.
     */
    fun toProfileOrNull(spec: ColorPresetSpec): ColorProfile? {
        if (spec.abiVersion < PluginAbi.MIN_SUPPORTED || spec.abiVersion > PluginAbi.VERSION) return null
        return toProfile(spec)
    }

    /**
     * Erzeugt ein [ColorProfile] aus [spec] mit geclamten Werten, builtIn = false, id = 0
     * (Room vergibt die echte id beim Upsert). Setzt keine ABI-Prüfung voraus — [toProfileOrNull]
     * verwenden, wenn die ABI-Kompatibilität noch unbekannt ist.
     */
    fun toProfile(spec: ColorPresetSpec): ColorProfile = ColorProfile(
        id = 0L,
        name = spec.name,
        saturation = spec.saturation.coerceIn(ColorProfile.SATURATION_MIN, ColorProfile.SATURATION_MAX),
        contrast = spec.contrast.coerceIn(ColorProfile.CONTRAST_MIN, ColorProfile.CONTRAST_MAX),
        brightness = spec.brightness.coerceIn(ColorProfile.BRIGHTNESS_MIN, ColorProfile.BRIGHTNESS_MAX),
        builtIn = false,
    )
}
