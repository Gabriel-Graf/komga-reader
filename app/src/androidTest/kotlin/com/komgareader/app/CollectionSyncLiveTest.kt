package com.komgareader.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.app.data.ActiveSource
import com.komgareader.app.data.CollectionSyncManager
import com.komgareader.app.data.KomgaSourceProvider
import com.komgareader.app.data.SourceRegistration
import com.komgareader.data.db.AppDatabase
import com.komgareader.data.repository.RoomCollectionRepository
import com.komgareader.data.repository.RoomServerRepository
import com.komgareader.data.security.KeystoreCredentialStore
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SourceKind
import com.komgareader.domain.repository.ServerConfig
import com.komgareader.domain.source.SourceManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E-Beweis für den bidirektionalen Collection-Sync gegen die lokale Test-Komga
 * (vom Emulator via 10.0.2.2:25600). Registriert EINE Komga-REST-Quelle (Admin-Key →
 * canWriteCollections()=true) und verdrahtet einen echten [CollectionSyncManager] über
 * [RoomCollectionRepository] + [ActiveSource] (agnostische Naht A).
 *
 * Test 1 (Discovery, Server→App) ist das Kern-Szenario: eine direkt am Server angelegte
 * Sammlung wird durch [CollectionSyncManager.fullSync] in die leere App gezogen.
 * Test 2 (Push→Server-Änderung→Pull, LWW) beweist die andere Richtung + Last-Writer-Wins.
 *
 * Jeder Test räumt seine Server-Sammlung in finally wieder ab, damit wiederholte Läufe
 * keine Sammlungen auf dem Server akkumulieren.
 */
@RunWith(AndroidJUnit4::class)
class CollectionSyncLiveTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: RoomServerRepository
    private lateinit var activeSource: ActiveSource
    private lateinit var collectionRepo: RoomCollectionRepository
    private lateinit var manager: CollectionSyncManager

    private companion object {
        const val BASE_URL = "http://10.0.2.2:25600/api/v1/"
        const val ADMIN_KEY = "2243c9f4ecc5404992ddf8eba4bf6488"
        const val BERSERK_SERIES_ID = "0QKVPRDV0293Z"
        const val SAGA_SERIES_ID = "0QKVPRDV42BFC"
    }

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        // Eindeutiger Keystore-Name pro Lauf — umgeht den bekannten Prefs-Wipe-Isolations-Bug.
        val store = KeystoreCredentialStore("collsync-live-${System.nanoTime()}")
        repo = RoomServerRepository(db.serverDao(), store)
        val sources = SourceManager()
        val registration = SourceRegistration(sources, KomgaSourceProvider())
        activeSource = ActiveSource(sources, repo, registration)
        collectionRepo = RoomCollectionRepository(db.collectionDao())
        manager = CollectionSyncManager(
            collectionRepo,
            resolver = { id -> activeSource.collectionSource(id) },
            allSources = { activeSource.allCollectionSources() },
            titleResolver = { sourceId, kind, remoteId ->
                if (kind == CollectionKind.SERIES) {
                    runCatching { activeSource.get(sourceId)?.seriesDetail(remoteId)?.title }.getOrNull()
                } else {
                    null
                }
            },
        )
    }

    @After fun tearDown() = db.close()

    private suspend fun registerKomga() {
        repo.save(ServerConfig(
            name = "Komga-REST",
            baseUrl = BASE_URL,
            apiKey = ADMIN_KEY,
            kind = SourceKind.KOMGA,
        ))
    }

    @Test fun discovery_zieht_server_sammlung_in_die_leere_app() = runTest {
        registerKomga()

        // Live-Quelle auflösen (genau die eine registrierte Komga-Quelle).
        val src = activeSource.allCollectionSources().first().second

        val uniqueName = "E2E-Discovery-${System.nanoTime()}"

        // SERVER-SEITIG SÄEN — unabhängig vom Pull-Pfad (direkter Quellen-Aufruf).
        src.createCollection(CollectionKind.SERIES, uniqueName, listOf(BERSERK_SERIES_ID))

        try {
            // Vorbedingung: die App kennt diese Sammlung noch nicht.
            assertTrue(
                "Lokale DB darf '$uniqueName' vor dem Sync nicht kennen",
                collectionRepo.collections.first().none { it.name == uniqueName },
            )

            // AKT: voller bidirektionaler Sync → Discovery zieht die Server-Sammlung herein.
            manager.fullSync()

            // ASSERT: lokal entdeckt …
            val discovered = collectionRepo.collections.first().firstOrNull { it.name == uniqueName }
            assertTrue(
                "Sammlung '$uniqueName' muss nach fullSync() lokal entdeckt sein",
                discovered != null,
            )
            // … mit der Berserk-Serie als Mitglied.
            assertTrue(
                "Entdeckte Sammlung muss Mitglied remoteId=$BERSERK_SERIES_ID tragen, " +
                    "war: ${discovered!!.members.map { it.remoteId }}",
                discovered.members.any { it.remoteId == BERSERK_SERIES_ID },
            )
        } finally {
            // IMMER aufräumen — auch bei Fehlschlag.
            src.listCollections(CollectionKind.SERIES)
                .firstOrNull { it.name == uniqueName }
                ?.let { src.deleteCollection(CollectionKind.SERIES, it.remoteId) }
        }
    }

    @Test fun push_dann_server_aenderung_wird_gepullt() = runTest {
        registerKomga()

        val resolved = activeSource.allCollectionSources().first()
        val src = resolved.second
        val sourceId = resolved.first

        val uniqueName = "E2E-PushPull-${System.nanoTime()}"

        // LOKAL anlegen, mit Berserk als einzigem Mitglied.
        val localId = collectionRepo.create(uniqueName, CollectionKind.SERIES)
        collectionRepo.setMembers(
            localId,
            listOf(CollectionMember(sourceId, BERSERK_SERIES_ID, "Berserk")),
        )

        try {
            // PUSH: fullSync schiebt die lokale Sammlung zum Server (Link → SYNCED).
            manager.fullSync()

            val onServer = src.listCollections(CollectionKind.SERIES).firstOrNull { it.name == uniqueName }
            assertTrue("Sammlung '$uniqueName' muss nach Push am Server existieren", onServer != null)
            assertTrue(
                "Server-Sammlung muss Berserk als Mitglied tragen",
                onServer!!.memberRemoteIds.contains(BERSERK_SERIES_ID),
            )

            // OFFLINE-Server-Änderung simulieren: Server tauscht das Mitglied auf Saga aus und
            // bumpt lastModifiedDate. (Eine LEERE Mitglieder-Liste lehnt Komga mit HTTP 400 ab —
            // eine Collection braucht mindestens eine Serie; daher Tausch auf eine ANDERE Serie
            // statt Leeren.) Da der Push den lokalen Link-updatedAt auf den Server-Stand zum
            // Push-Zeitpunkt gesetzt hat und dieses Update DANACH passiert, gewinnt der Server per LWW.
            src.updateCollection(CollectionKind.SERIES, onServer.remoteId, uniqueName, listOf(SAGA_SERIES_ID))

            // Lokalen Link künstlich „alt" stempeln, damit der Server-Stand eindeutig neuer ist
            // (sonst entscheidet bei ~gleichem Zeitstempel der LWW-Tie zugunsten lokal → Pull übersprungen, Test-Flake).
            val link = collectionRepo.syncLinks(localId).first().first()
            collectionRepo.updateSyncLink(link.copy(updatedAt = 1L))

            // PULL: fullSync zieht die Server-Mitglieder-Liste (LWW, Server neuer).
            manager.fullSync()

            val afterPull = collectionRepo.collections.first().firstOrNull { it.name == uniqueName }
            assertTrue("Lokale Sammlung '$uniqueName' muss nach Pull noch existieren", afterPull != null)
            assertEquals(
                "Lokale Sammlung muss nach Pull (LWW Server neuer) genau das Server-Mitglied Saga tragen, " +
                    "war: ${afterPull!!.members.map { it.remoteId }}",
                listOf(SAGA_SERIES_ID),
                afterPull.members.map { it.remoteId },
            )
        } finally {
            src.listCollections(CollectionKind.SERIES)
                .firstOrNull { it.name == uniqueName }
                ?.let { src.deleteCollection(CollectionKind.SERIES, it.remoteId) }
        }
    }

    /**
     * Trennen+Wiederverbinden darf am Server NICHTS löschen oder leeren: Trennen ist lokal-only,
     * Wiederverbinden ist Pull-only. Beweist beide Nutzer-Sorgen: (1) Disconnect löscht nicht am
     * Server, (2) der Reconnect-Pull pusht/löscht nicht, sondern entdeckt nur erneut.
     */
    @Test fun reconnect_pullt_nur_und_loescht_nichts_am_server() = runTest {
        registerKomga()

        val resolved = activeSource.allCollectionSources().first()
        val src = resolved.second
        val sourceId = resolved.first

        val uniqueName = "E2E-Reconnect-${System.nanoTime()}"

        // LOKAL anlegen, mit Berserk als einzigem Mitglied, dann per fullSync zum Server pushen.
        val localId = collectionRepo.create(uniqueName, CollectionKind.SERIES)
        collectionRepo.setMembers(
            localId,
            listOf(CollectionMember(sourceId, BERSERK_SERIES_ID, "Berserk")),
        )

        try {
            manager.fullSync()
            assertTrue(
                "Sammlung '$uniqueName' muss nach Push am Server existieren",
                src.listCollections(CollectionKind.SERIES).any { it.name == uniqueName },
            )

            // DISCONNECT: nur lokal aufräumen (genau das, was removeServer via removeSource macht).
            collectionRepo.removeSource(sourceId)
            assertTrue(
                "Nach dem Trennen darf '$uniqueName' lokal NICHT mehr existieren",
                collectionRepo.collections.first().none { it.name == uniqueName },
            )
            assertTrue(
                "Trennen darf die Sammlung am Server NICHT löschen",
                src.listCollections(CollectionKind.SERIES).any { it.name == uniqueName },
            )

            // RECONNECT: Pull-only zieht die Server-Sammlung wieder herein.
            manager.pullOnlySync()
            val rediscovered = collectionRepo.collections.first().firstOrNull { it.name == uniqueName }
            assertTrue(
                "pullOnlySync muss '$uniqueName' wieder lokal entdecken",
                rediscovered != null,
            )
            assertTrue(
                "Wiederentdeckte Sammlung muss Berserk als Mitglied tragen, " +
                    "war: ${rediscovered!!.members.map { it.remoteId }}",
                rediscovered.members.any { it.remoteId == BERSERK_SERIES_ID },
            )
            // Der Pull darf die Server-Sammlung NICHT geleert oder gelöscht haben.
            val onServerAfter = src.listCollections(CollectionKind.SERIES).firstOrNull { it.name == uniqueName }
            assertTrue("Pull-only darf '$uniqueName' am Server NICHT löschen", onServerAfter != null)
            assertTrue(
                "Pull-only darf '$uniqueName' am Server NICHT leeren, " +
                    "war: ${onServerAfter!!.memberRemoteIds}",
                onServerAfter.memberRemoteIds.contains(BERSERK_SERIES_ID),
            )
        } finally {
            src.listCollections(CollectionKind.SERIES)
                .firstOrNull { it.name == uniqueName }
                ?.let { src.deleteCollection(CollectionKind.SERIES, it.remoteId) }
        }
    }
}
