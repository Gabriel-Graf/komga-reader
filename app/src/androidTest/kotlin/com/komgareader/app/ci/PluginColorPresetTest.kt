package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.plugin.host.PluginHost
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E des data-only Color-Preset-Plugins: PluginHost entdeckt das installierte Preset-APK und parst
 * dessen ColorPresetSpec(s) aus dem Asset. Server-los — nur PackageManager + Asset-Lesen.
 * Voraussetzung: das Kindle-Preset-APK ist installiert (Plan 6 Task 1).
 */
@RunWith(AndroidJUnit4::class)
class PluginColorPresetTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)

    @Test fun preset_plugin_entdeckt_und_specs_geparst() {
        val presetPlugins = host.discoverColorPresetPlugins()
        assertTrue("Kein Color-Preset-Plugin entdeckt (Preset-APK installiert?)", presetPlugins.isNotEmpty())

        val pkg = presetPlugins.first()
        assertTrue("Entdecktes Preset-Plugin muss ABI 1 haben", pkg.abiVersion == 1)
        assertTrue("Preset-Plugin muss mind. ein ColorPresetSpec liefern, war: ${pkg.presets.size}", pkg.presets.isNotEmpty())
        val spec = pkg.presets.first()
        assertNotNull("ColorPresetSpec braucht einen Namen", spec.name)
        assertTrue("ColorPresetSpec-Name darf nicht leer sein", spec.name.isNotBlank())
    }
}
