package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.plugin.host.PluginHost
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E des data-only Color-Preset-Plugins: PluginHost entdeckt das installierte Preset-APK und parst
 * dessen ColorPresetSpec(s) aus dem Asset. Server-los — nur PackageManager + Asset-Lesen.
 * Voraussetzung: das Kindle-Preset-APK ist installiert (Plan 6 Task 1). Ist es NICHT installiert
 * (z.B. CI ohne APK-Provisioning), wird der Test übersprungen statt rot — Präkondition, kein Fehler.
 */
@RunWith(AndroidJUnit4::class)
class PluginColorPresetTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)
    private val presetPkg = "com.komgareader.preset.kindle"

    private fun installed(pkg: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(pkg, 0); true }.getOrDefault(false)

    @Test fun preset_plugin_entdeckt_und_specs_geparst() {
        assumeTrue("Preset-APK '$presetPkg' nicht installiert — übersprungen (Plan 6 Task 1 / CI-APK-Provisioning)", installed(presetPkg))

        val presetPlugins = host.discoverColorPresetPlugins()
        assertTrue("Kein Color-Preset-Plugin entdeckt (Preset-APK installiert?)", presetPlugins.isNotEmpty())

        val pkg = presetPlugins.first()
        assertTrue("Entdecktes Preset-Plugin muss ABI 1 haben", pkg.abiVersion == 1)
        assertTrue("Preset-Plugin muss mind. ein ColorPresetSpec liefern, war: ${pkg.presets.size}", pkg.presets.isNotEmpty())
        val spec = pkg.presets.first()
        assertTrue("ColorPresetSpec-Name darf nicht leer sein", spec.name.isNotBlank())
    }
}
