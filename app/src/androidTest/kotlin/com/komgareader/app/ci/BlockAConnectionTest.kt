package com.komgareader.app.ci

import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4

/** Spec §9 Block A — Verbindung & Multi-Source (Naht A). Live gegen die CI-Komga-Instanzen. */
@RunWith(AndroidJUnit4::class)
class BlockAConnectionTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    private suspend fun titlesOf(source: com.komgareader.domain.source.BrowsableSource): List<String> =
        source.browse(0, SourceFilter()).items.map { it.title }

    /** A1: Eine Quelle verbunden → Bibliothek der Quelle ist browsebar. */
    @Test fun a1_eine_quelle_verbunden_liefert_bibliothek() = runTest {
        stack.register(CiKomga.A)
        val all = stack.activeSource.all()
        assertEquals("Genau eine Quelle erwartet", 1, all.size)
        val titles = titlesOf(all.first())
        assertTrue("Manga-Serie muss erscheinen: $titles", titles.contains(CiFixtures.MANGA_SERIES))
    }

    /** A2: n unterschiedliche Server (A+B) → all() aggregiert beide, sourceId stimmt je Werk. */
    @Test fun a2_zwei_unterschiedliche_server_aggregiert() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val all = stack.activeSource.all()
        assertEquals("Zwei Quellen erwartet", 2, all.size)

        val fromA = all.first { CiFixtures.MANGA_SERIES in titlesOf(it) }
        val fromB = all.first { CiFixtures.WEBTOON_SERIES in titlesOf(it) }
        assertNotEquals("A und B müssen unterschiedliche sourceIds haben", fromA.id, fromB.id)

        // Jede Serie trägt die sourceId IHRER Quelle (nicht „die erste/aktive").
        val mangaSeries = fromA.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
        assertEquals("Manga-Serie muss sourceId von A tragen", fromA.id, mangaSeries.sourceId)
        val webtoonSeries = fromB.browse(0, SourceFilter()).items.first { it.title == CiFixtures.WEBTOON_SERIES }
        assertEquals("Webtoon-Serie muss sourceId von B tragen", fromB.id, webtoonSeries.sourceId)
    }

    /** A3a: derselbe Server zweimal registriert → stabile sourceId → eine Quelle (Dedup). */
    @Test fun a3_gleicher_server_doppelt_dedupliziert() = runTest {
        // Zweite Registrierung mit identischer Konfiguration (anderer Settings-Eintrag, gleiche URL/Creds).
        stack.register(CiKomga.A, CiKomga.A.copy(name = CiKomga.A.name))
        val all = stack.activeSource.all()
        assertEquals("Identische Quelle darf nur EINE sourceId erzeugen (deterministischer Hash)", 1, all.size)
    }

    /** A3b: Spiegel-Server C (gleicher Inhalt, andere URL) → zwei verschiedene sourceIds. */
    @Test fun a3_spiegel_server_zwei_quellen() = runTest {
        stack.register(CiKomga.A, CiKomga.C)
        val all = stack.activeSource.all()
        assertEquals("A und Spiegel-C sind zwei verschiedene Quellen", 2, all.size)
        // Gleicher Inhalt, aber getrennte Identitäten.
        assertNotEquals(all[0].id, all[1].id)
    }

    /** A4: Server entfernen → er verschwindet aus all(), die übrige Bibliothek bleibt. */
    @Test fun a4_server_entfernen_bricht_bibliothek_nicht() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        assertEquals(2, stack.activeSource.all().size)

        // B (zweite gespeicherte Verbindung) hat Rowid 2 (A=1). Robuster: über configs die Rowid finden.
        stack.remove(2)
        val all = stack.activeSource.all()
        assertEquals("Nach Entfernen bleibt eine Quelle", 1, all.size)
        assertEquals("Verbleibende Quelle ist A (KOMGA, Manga)", SourceKind.KOMGA, all.first().kind)
        assertTrue("A bleibt browsebar", CiFixtures.MANGA_SERIES in titlesOf(all.first()))
    }
}
