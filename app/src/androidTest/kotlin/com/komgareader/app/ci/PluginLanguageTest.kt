package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.data.plugin.parseLanguageSpec
import com.komgareader.plugin.PluginCategory
import com.komgareader.plugin.host.PluginHost
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E des data-only Sprach-Plugins: PluginHost entdeckt das installierte Sprach-APK und parst
 * dessen LanguageSpec aus dem Asset. Server-los — nur PackageManager + Asset-Lesen.
 * Voraussetzung: das ES-Sprach-APK ist installiert. Ist es NICHT installiert (z.B. CI ohne
 * APK-Provisioning), wird der Test übersprungen statt rot — Präkondition, kein Fehler.
 */
@RunWith(AndroidJUnit4::class)
class PluginLanguageTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)
    private val pkg = "com.komgareader.lang.es"

    private fun installed(p: String): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(p, 0); true }.getOrDefault(false)

    @Test fun language_plugin_entdeckt_und_geparst() {
        assumeTrue("Sprach-APK '$pkg' nicht installiert — übersprungen", installed(pkg))

        val data = host.discoverDataPlugins(PluginCategory.LANGUAGE)
        assertTrue("Kein LANGUAGE-Plugin entdeckt", data.isNotEmpty())

        val spec = parseLanguageSpec(data.first().assetJson, data.first().abiVersion)
        assertTrue(
            "Spec muss Code+Strings tragen",
            spec != null && spec.code.isNotBlank() && spec.strings.isNotEmpty(),
        )
    }
}
