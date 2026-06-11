package com.komgareader.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceManager
import com.komgareader.plugin.host.PluginHost
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Lackmustest Naht A (verschärft): Komga-REST **und** OPDS gleichzeitig live, gemischt.
 *
 * Registriert beide Quellen gegen dieselbe lokale Test-Komga (vom Emulator via 10.0.2.2:25600),
 * aggregiert die Bibliothek über [ActiveSource.all()], filtert ein Werk der OPDS-Quelle heraus
 * und lädt seine Binärdaten über [com.komgareader.domain.source.BrowsableSource.downloadFile].
 *
 * OPDS unterstützt kein seitenweises Streaming ([BrowsableSource.openPage] wirft), liest
 * also über [downloadFile] — das ist der korrekte Pfad für alle Download-/Reflow-Reader
 * (MuPDF, crengine). Damit ist die Naht trotzdem vollständig bewiesen.
 *
 * Test schlägt fehl, wenn:
 * - OPDS keine Credentials bekommt (401 / leere Antwort),
 * - [ActiveSource.all()] nicht beide Quellen liefert,
 * - kein Werk der OPDS-Quelle aggregiert wird,
 * - [downloadFile] weniger als 1 KiB liefert.
 */
@RunWith(AndroidJUnit4::class)
class MixedSourcesLiveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RoomServerRepository
    private lateinit var activeSource: ActiveSource

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val store = KeystoreCredentialStore("mixed-live-${System.nanoTime()}")
        repo = RoomServerRepository(db.serverDao(), store)
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider(), PluginHost(ctx))
        activeSource = ActiveSource(sources, repo, registration)
    }

    @After fun tearDown() = db.close()

    @Test fun komga_und_opds_gleichzeitig_live_gemischt() = runTest {
        // Beide Quellen zeigen auf dieselbe Test-Komga — Komga-REST über API-Key,
        // OPDS über Basic-Auth (Komga OPDS akzeptiert keinen X-API-Key).
        // Der OPDS-Catalog zeigt auf die Berserk-Serie (Nachweis: Buch mit Acquisition-Link).
        repo.save(ServerConfig(
            name = "Komga-REST",
            baseUrl = "http://10.0.2.2:25600/api/v1/",
            apiKey = "2243c9f4ecc5404992ddf8eba4bf6488",
            kind = SourceKind.KOMGA,
        ))
        repo.save(ServerConfig(
            name = "Komga-OPDS",
            // Direkt auf die Berserk-Serie zeigen → Eintrag hat Acquisition-Link
            baseUrl = "http://10.0.2.2:25600/opds/v1.2/series/0QKVPRDV0293Z",
            username = "admin@test.local",   // bekannte Fixture-Credentials der lokalen Test-Komga (siehe local-test-komga)
            password = "testpass123",          // projektweites Muster — alle Instrumented-Tests hardcoden diese Werte
            kind = SourceKind.OPDS,
        ))

        // Beide Quellen müssen aggregiert werden.
        val all = activeSource.all()
        assertTrue("Mindestens 2 Quellen erwartet, war: ${all.size}", all.size >= 2)

        // Quellenarten prüfen.
        assertTrue("Komga-REST muss in all() sein", all.any { it.kind == SourceKind.KOMGA })
        assertTrue("OPDS muss in all() sein", all.any { it.kind == SourceKind.OPDS })

        // IDs müssen unterschiedlich sein — sonst kein echter gemischter Betrieb möglich.
        val komgaSource = all.first { it.kind == SourceKind.KOMGA }
        val opdsSource = all.first { it.kind == SourceKind.OPDS }
        assertNotEquals(
            "Quellen müssen unterschiedliche IDs haben — sonst kein echter gemischter Betrieb",
            komgaSource.id,
            opdsSource.id,
        )

        // Aus der OPDS-Quelle browsen.
        val browsePage = opdsSource.browse(0, com.komgareader.domain.source.SourceFilter())
        assertTrue(
            "OPDS-Katalog muss mindestens ein Werk enthalten, war: ${browsePage.items.size}",
            browsePage.items.isNotEmpty(),
        )

        // Die sourceId des geholten Werks muss zur OPDS-Quelle gehören.
        val opdsItem = browsePage.items.first()
        assertTrue(
            "Werk muss zur OPDS-Quelle gehören: sourceId=${opdsItem.sourceId}, opds.id=${opdsSource.id}",
            opdsItem.sourceId == opdsSource.id,
        )

        // Werk über die agnostische Naht laden — OPDS liest über downloadFile (kein openPage).
        val fileBytes = opdsSource.downloadFile(opdsItem.remoteId)
        assertTrue(
            "Heruntergeladene Bytes müssen > 1 KiB sein — leer = Auth oder Download fehlgeschlagen (${fileBytes.size}B)",
            fileBytes.size > 1024,
        )
    }
}
