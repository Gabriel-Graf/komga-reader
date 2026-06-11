package com.komgareader.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.source.BrowsableSource
import com.komgareader.domain.source.SourceFilter
import com.komgareader.plugin.host.PluginHost
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E-Beweis des Phase-4-Plugin-Loaders gegen die echte Docker-Kavita ([[local-test-kavita]],
 * vom Emulator via 10.0.2.2:5001). Beweist die **ganze Kette** auf dem Gerät:
 * Entdeckung (PackageManager) → ABI-Gate → TOFU-Signatur → Laden (createPackageContext) →
 * Lesen über die Naht ([BrowsableSource.browse]/[books]/[pages]/[openPage]).
 *
 * Voraussetzungen: das Kavita-Plugin-APK (`com.komgareader.plugin.kavita`) ist installiert
 * und die Docker-Kavita läuft mit dem Seed (Serien „Solo Leveling"/„Chainsaw Man").
 *
 * Schlägt fehl, wenn: das Plugin nicht entdeckt wird, die Signatur nicht gepinnt geladen wird,
 * browse keine Seed-Serie liefert, oder openPage keine echten Bildbytes liefert.
 */
@RunWith(AndroidJUnit4::class)
class KavitaPluginLiveTest {

    private val ctx = ApplicationProvider.getApplicationContext<Context>()
    private val host = PluginHost(ctx)
    private val pkg = "com.komgareader.plugin.kavita"
    private val config = mapOf(
        "url" to "http://10.0.2.2:5001",
        "apiKey" to "Y7UtfX3nmU8MBg40cqID62BYeuewLnpJ",
    )

    @Test fun kavita_plugin_entdeckt_geladen_und_liefert_inhalt() = runTest {
        val kavita = host.discoverPlugins().firstOrNull { it.packageName == pkg }
        assertNotNull("Kavita-Plugin nicht entdeckt (installiert?)", kavita)
        kavita!!
        assertEquals(1, kavita.abiVersion)
        assertEquals("Kavita", kavita.metadata.displayName)
        assertTrue("Signatur-Digest fehlt", kavita.signatureSha256.isNotBlank())
        assertTrue("url-Feld fehlt im ConfigSchema", kavita.configSchema.fields.any { it.key == "url" })
        assertTrue("apiKey-Feld fehlt im ConfigSchema", kavita.configSchema.fields.any { it.key == "apiKey" })

        val source = host.sourceFor(kavita.packageName, kavita.entryClass, kavita.signatureSha256, config)
        assertNotNull("sourceFor lieferte null (Signatur-Pin/Load fehlgeschlagen?)", source)
        source as BrowsableSource

        val firstPage = source.browse(1, SourceFilter())
        val titles = firstPage.items.map { it.title }
        assertTrue(
            "Seed-Serien fehlen in browse(): $titles",
            titles.any { it.contains("Solo Leveling") || it.contains("Chainsaw Man") },
        )

        val series = firstPage.items.first {
            it.title.contains("Solo Leveling") || it.title.contains("Chainsaw Man")
        }
        val books = source.books(series.remoteId)
        assertTrue("Serie ohne Kapitel/Bücher", books.isNotEmpty())
        val pages = source.pages(books.first().remoteId)
        assertTrue("Buch ohne Seiten", pages.isNotEmpty())
        val bytes = source.openPage(pages.first())
        // >1 KiB = echtes Bild (konsistent mit MixedSourcesLiveTest), nicht nur eine Fehlerseite.
        assertTrue("Seitenbytes zu klein für echtes Bild: ${bytes.size}", bytes.size > 1024)
    }

    @Test fun falscher_signatur_pin_blockiert_das_laden() = runTest {
        val kavita = host.discoverPlugins().firstOrNull { it.packageName == pkg }
        assertNotNull("Kavita-Plugin nicht entdeckt (installiert?)", kavita)
        kavita!!
        val falscherPin = "deadbeef".repeat(8) // 64 hex, garantiert != echter Cert
        val source = host.sourceFor(kavita.packageName, kavita.entryClass, falscherPin, config)
        assertNull("Falscher Signatur-Pin darf NICHT laden (TOFU-Gate)", source)
    }
}
