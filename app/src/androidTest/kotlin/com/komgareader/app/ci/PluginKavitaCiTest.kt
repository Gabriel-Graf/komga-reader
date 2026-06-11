package com.komgareader.app.ci

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.host.PluginHost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Source-Plugin-Lade-Kette gegen die reproduzierbare CI-Kavita (Plan 6 Task 4, Port 25710). URL/apiKey
 * via Instrumentation-Argument (`kavitaUrl`/`kavitaKey`), vom Fixture-Seed (`up.sh` → `.keys.env`) an die
 * Gradle-Invocation gereicht. Ohne Argument (lokaler Ad-hoc-Lauf) wird der Test übersprungen statt rot —
 * der manuelle Pfad ist [com.komgareader.app.KavitaPluginLiveTest] gegen den Dev-Container (Port 5001).
 *
 * Beweist auf dem Gerät: Entdeckung (PackageManager) → TOFU-Signatur → Laden → Lesen über die Naht
 * ([BrowsableSource.browse]) gegen eine frisch geseedete Kavita.
 */
@RunWith(AndroidJUnit4::class)
class PluginKavitaCiTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)
    private val pkg = "com.komgareader.plugin.kavita"

    @Test fun kavita_plugin_gegen_ci_kavita() = runTest {
        val args = InstrumentationRegistry.getArguments()
        val url = args.getString("kavitaUrl")
        val key = args.getString("kavitaKey")
        assumeTrue(
            "kavitaUrl/kavitaKey nicht gesetzt — übersprungen (manueller Pfad: KavitaPluginLiveTest)",
            url != null && key != null,
        )

        val discovered = host.discoverPlugins().firstOrNull { it.packageName == pkg }
        assertNotNull("Kavita-Plugin nicht entdeckt (installiert?)", discovered)
        discovered!!

        val source = host.sourceFor(
            discovered.packageName,
            discovered.entryClass,
            discovered.signatureSha256,
            mapOf("url" to url!!, "apiKey" to key!!),
        )
        assertNotNull("sourceFor lieferte null (Signatur-Pin/Load fehlgeschlagen?)", source)
        source as BrowsableSource

        val items = source.browse(1, SourceFilter()).items
        assertTrue("CI-Kavita muss mind. eine Serie liefern, war: ${items.map { it.title }}", items.isNotEmpty())
    }
}
