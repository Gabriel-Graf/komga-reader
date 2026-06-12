package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.plugin.parseReaderPresetSpecs
import com.komgareader.plugin.PluginCategory
import com.komgareader.plugin.host.PluginHost
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E des data-only Reader-Preset-Plugins: PluginHost entdeckt das installierte Preset-APK und parst
 * dessen ReaderPreset-Liste aus dem Asset. Server-los — nur PackageManager + Asset-Lesen.
 * Voraussetzung: das E-Ink-Reader-Preset-APK ist installiert. Ist es NICHT installiert (z.B. CI ohne
 * APK-Provisioning), wird der Test übersprungen statt rot — Präkondition, kein Fehler.
 */
@RunWith(AndroidJUnit4::class)
class PluginReaderPresetTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)
    private val pkg = "com.komgareader.preset.reader.eink"

    private fun installed(p: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)

    @Test fun reader_preset_plugin_entdeckt_und_geparst() {
        assumeTrue("Reader-Preset-APK '$pkg' nicht installiert — übersprungen", installed(pkg))

        val data = host.discoverDataPlugins(PluginCategory.READER_PRESET)
        assertTrue("Kein READER_PRESET-Plugin entdeckt", data.isNotEmpty())

        val presets = parseReaderPresetSpecs(data.first().assetJson, data.first().abiVersion)
        assertTrue("Preset-Liste darf nicht null sein", presets != null)
        assertTrue("Preset-Liste muss mind. einen Eintrag enthalten, war: ${presets?.size}", presets!!.isNotEmpty())
        assertTrue("Erstes Preset muss einen nicht-leeren Namen haben", presets.first().name.isNotBlank())
    }
}
