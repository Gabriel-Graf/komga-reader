# Integrationstest — Plan 5: Multi-Source Collection-Sync (D15/D16) + Fortschritts-Round-Trip (E)

> **For agentic workers:** REQUIRED SUB-SKILL: subagent-driven-development oder executing-plans. Checkbox-Steps.
> **Pflicht-Skill vor dem Bauen:** `komga-collection-server-sync` (die 5 Sync-Invarianten).

**Goal:** Die Integrationstest-Lücken schließen, die der (gemergte) Single-Server-`CollectionSyncLiveTest`
nicht abdeckt: **Multi-Source-Collection-Sync** über die CI-Topologie (A+B) — eine App-Sammlung mit
Mitgliedern aus zwei Servern syncт jedes Subset zu **seiner** Quelle (D15), und `removeSource` einer
Quelle behält die Mitglieder der **anderen** Quelle + löscht **nichts** am Server (D16, Invariante #2).
Plus der vom Nutzer gewünschte **Lese-Fortschritt-Round-Trip über eine frische Verbindung**: Fortschritt
auf „Gerät 1" gesetzt → landet am Server → eine **frische lokale DB** („reconnect/Neuinstallation",
selber User) zieht ihn per Pull zurück.

**Architecture:** Baut auf der Plan-2-Harness. `CiSourceStack` wird um die Collection-Verdrahtung
erweitert (`collectionRepo` + ein echter `CollectionSyncManager`, identisch zum `CollectionSyncLiveTest`-
Wiring, an einer Stelle = DRY). Der Round-Trip nutzt **zwei** `CiSourceStack`-Instanzen gegen **denselben**
Server A: jede hat ihre eigene in-memory-DB → Stack-2 = „lokaler Stand weg, neu verbunden".

**Tech Stack:** wie Plan 2/3 (AndroidJUnit4, inMemory-Room, coroutines-test). Emulator `emulator-5554`,
CI-Fixtures laufen (`tools/ci-fixtures/up.sh`).

**Bezug:** Spec §9 Block D (D15/D16/D17) + Block E. Skill `komga-collection-server-sync`. Ergänzt den
gemergten `app/src/androidTest/.../CollectionSyncLiveTest.kt` (Single-Server: Discovery/Push-Pull-LWW/
Reconnect) um die Multi-Source-Dimension.

---

## Grounding (verifiziert gegen den gemergten master-Code)

- `CollectionSyncSource` (Naht-A-Capability): `canWriteCollections()`, `listCollections(kind)`,
  `createCollection(kind, name, memberRemoteIds)`, `updateCollection(...)`, `deleteCollection(kind, remoteId)`.
  `KomgaSource` implementiert es; mit Admin-Creds (CiKomga) ist `canWriteCollections()==true`.
- `ActiveSource` (master): `collectionSource(sourceId): CollectionSyncSource?`,
  `allCollectionSources(): List<Pair<Long, CollectionSyncSource>>`.
- `CollectionRepository`/`RoomCollectionRepository(db.collectionDao())`: `create(name, kind): Long`,
  `setMembers(id, List<CollectionMember>)`, `collections: Flow<List<UserCollection>>`,
  `syncLinks(id): Flow<List<CollectionSyncLink>>`, `updateSyncLink(link)`, `removeSource(sourceId)`,
  `get(id): UserCollection?`.
- `CollectionMember(sourceId: Long, remoteId: String, title: String)`. `CollectionKind.SERIES`.
- `CollectionSyncManager(repo, resolver = {id -> active.collectionSource(id)}, allSources = {active.allCollectionSources()}, titleResolver = {sourceId,kind,remoteId -> …seriesDetail(remoteId)?.title})`;
  `fullSync(): List<VanishedCollection>` (push+pull+discovery), `pullOnlySync()` (nur pull).
- `SyncingSource.pushProgress(bookRemoteId, ReadProgress)`, `pullProgress(bookRemoteId): ReadProgress?`.
  `ReadProgress(bookId, page, totalPages, completed=false, locator=null, dirty=false, updatedAt)`.
- Fixtures: A=25701 (`Sample-Manga`), B=25702 (`Sample-Webtoon`). `CiKomga.A/B`, `CiFixtures`.
- **Invarianten (Skill):** Connect=pull-only, Disconnect=`removeSource` (nur lokal, nie Server-Löschung),
  multi-source-Sammlung behält bei `removeSource` die anderen Quellen. Member-Identität `(sourceId, remoteId)`.

---

## Task 1: CiSourceStack um Collection-Verdrahtung erweitern (DRY)

**Files:**
- Modify: `app/src/androidTest/kotlin/com/komgareader/app/ci/CiSourceStack.kt`

- [ ] **Step 1: collectionRepo + syncManager ergänzen**

In `CiSourceStack` (nach dem bestehenden `activeSource`-Feld) ergänzen:
```kotlin
    // Collection-Sync-Verdrahtung (identisch zu CollectionSyncManager-Produktion / CollectionSyncLiveTest).
    val collectionRepo = com.komgareader.data.repository.RoomCollectionRepository(db.collectionDao())
    val collectionSyncManager = com.komgareader.app.data.CollectionSyncManager(
        collectionRepo,
        resolver = { id -> activeSource.collectionSource(id) },
        allSources = { activeSource.allCollectionSources() },
        titleResolver = { sourceId, kind, remoteId ->
            if (kind == com.komgareader.domain.model.CollectionKind.SERIES) {
                runCatching { activeSource.get(sourceId)?.seriesDetail(remoteId)?.title }.getOrNull()
            } else {
                null
            }
        },
    )
```
> Hinweis: `db` ist heute `private` in `CiSourceStack` — falls `db.collectionDao()` von außen nötig wäre,
> bleibt es gekapselt (collectionRepo/Manager sind die öffentliche Grenze). `db` bleibt private.

- [ ] **Step 2: Kompiliert**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/CiSourceStack.kt
git commit -m "test(ci): CiSourceStack um Collection-Sync-Verdrahtung erweitert (DRY)"
```

---

## Task 2: Block D — Multi-Source-Collection-Sync (D15/D16)

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockDCollectionSyncTest.kt`

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.source.CollectionSyncSource
import com.komgareader.domain.source.SourceFilter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §9 Block D — Multi-Source-Collection-Sync über die CI-Topologie (A+B). Ergänzt den
 * Single-Server-CollectionSyncLiveTest um die Mehrquellen-Dimension. Skill: komga-collection-server-sync.
 */
@RunWith(AndroidJUnit4::class)
class BlockDCollectionSyncTest {

    private lateinit var stack: CiSourceStack

    @Before fun setUp() { stack = CiSourceStack() }
    @After fun tearDown() { stack.close() }

    private suspend fun seriesRemoteId(sourceId: Long, title: String): String {
        val src = stack.activeSource.get(sourceId)!!
        return src.browse(0, SourceFilter()).items.first { it.title == title }.remoteId
    }

    private suspend fun collSource(idx: Int): Pair<Long, CollectionSyncSource> =
        stack.activeSource.allCollectionSources()[idx]

    private suspend fun cleanup(name: String) {
        for ((_, src) in stack.activeSource.allCollectionSources()) {
            src.listCollections(CollectionKind.SERIES).firstOrNull { it.name == name }
                ?.let { runCatching { src.deleteCollection(CollectionKind.SERIES, it.remoteId) } }
        }
    }

    /**
     * D15: Eine App-Sammlung mit Mitgliedern aus ZWEI Servern (A: Manga, B: Webtoon) syncт jedes
     * Subset zu SEINER Quelle — A bekommt die Manga-Sammlung, B die Webtoon-Sammlung (gleicher Name).
     */
    @Test fun d15_cross_source_collection_synct_je_subset_zur_eigenen_quelle() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val sources = stack.activeSource.allCollectionSources()
        assertTrue("Zwei collection-fähige Quellen erwartet", sources.size >= 2)
        val aId = sources.first { it.second.name.contains("A") }.first
        val bId = sources.first { it.second.name.contains("B") }.first

        val mangaRid = seriesRemoteId(aId, CiFixtures.MANGA_SERIES)
        val webtoonRid = seriesRemoteId(bId, CiFixtures.WEBTOON_SERIES)
        val name = "CI-Cross-${System.nanoTime()}"

        // Lokale Sammlung mit je einem Mitglied aus A und B.
        val localId = stack.collectionRepo.create(name, CollectionKind.SERIES)
        stack.collectionRepo.setMembers(
            localId,
            listOf(
                CollectionMember(aId, mangaRid, CiFixtures.MANGA_SERIES),
                CollectionMember(bId, webtoonRid, CiFixtures.WEBTOON_SERIES),
            ),
        )

        try {
            stack.collectionSyncManager.fullSync()

            val onA = stack.activeSource.get(aId) as CollectionSyncSource
            val onB = stack.activeSource.get(bId) as CollectionSyncSource
            val aColl = onA.listCollections(CollectionKind.SERIES).firstOrNull { it.name == name }
            val bColl = onB.listCollections(CollectionKind.SERIES).firstOrNull { it.name == name }
            assertTrue("Server A muss die Sammlung tragen", aColl != null)
            assertTrue("Server B muss die Sammlung tragen", bColl != null)
            assertEquals("A-Sammlung enthält genau das Manga-Mitglied", listOf(mangaRid), aColl!!.memberRemoteIds)
            assertEquals("B-Sammlung enthält genau das Webtoon-Mitglied", listOf(webtoonRid), bColl!!.memberRemoteIds)
        } finally {
            cleanup(name)
        }
    }

    /**
     * D16: removeSource(A) auf einer synchronisierten Multi-Source-Sammlung behält die Mitglieder
     * der anderen Quelle (B) lokal — und löscht NICHTS am Server (Invariante #2, Disconnect=lokal).
     */
    @Test fun d16_remove_source_behaelt_andere_quelle_und_loescht_nichts_am_server() = runTest {
        stack.register(CiKomga.A, CiKomga.B)
        val sources = stack.activeSource.allCollectionSources()
        val aId = sources.first { it.second.name.contains("A") }.first
        val bId = sources.first { it.second.name.contains("B") }.first
        val mangaRid = seriesRemoteId(aId, CiFixtures.MANGA_SERIES)
        val webtoonRid = seriesRemoteId(bId, CiFixtures.WEBTOON_SERIES)
        val name = "CI-Remove-${System.nanoTime()}"

        val localId = stack.collectionRepo.create(name, CollectionKind.SERIES)
        stack.collectionRepo.setMembers(
            localId,
            listOf(
                CollectionMember(aId, mangaRid, CiFixtures.MANGA_SERIES),
                CollectionMember(bId, webtoonRid, CiFixtures.WEBTOON_SERIES),
            ),
        )

        try {
            stack.collectionSyncManager.fullSync()

            // DISCONNECT A: nur lokales Cleanup der A-Mitglieder.
            stack.collectionRepo.removeSource(aId)

            val local = stack.collectionRepo.collections.first().firstOrNull { it.name == name }
            assertTrue("Sammlung muss lokal bestehen bleiben (B-Mitglied übrig)", local != null)
            assertTrue(
                "A-Mitglied (Manga) muss lokal weg sein, war: ${local!!.members.map { it.remoteId }}",
                local.members.none { it.remoteId == mangaRid },
            )
            assertTrue(
                "B-Mitglied (Webtoon) muss lokal erhalten bleiben",
                local.members.any { it.remoteId == webtoonRid },
            )

            // Server A darf die Sammlung NICHT verloren haben (Disconnect ist lokal-only).
            val onA = stack.activeSource.get(aId) as CollectionSyncSource
            assertTrue(
                "removeSource darf die Sammlung am Server A NICHT löschen",
                onA.listCollections(CollectionKind.SERIES).any { it.name == name },
            )
        } finally {
            cleanup(name)
        }
    }
}
```

> Implementer-Hinweis: Die Quellen-Zuordnung über `name.contains("A")`/`"B")` nutzt die CiKomga-Namen
> (`CI-Komga-A`/`-B`). Falls `allCollectionSources()` die Reihenfolge anders liefert, ist die
> name-basierte Auswahl robust. Falls `get(sourceId)` nicht zu `CollectionSyncSource` castbar ist
> (sollte: KomgaSource implementiert beide), `collectionSource(sourceId)` nutzen. Bei Abweichung
> vermerken. **Echtes Finding nicht verstecken:** synct A's Subset fälschlich zu B (oder löscht
> removeSource am Server) → BLOCKED, das verletzt eine Invariante.

- [ ] **Step 2: Kompiliert + ausführen**

Run: `./gradlew :app:compileDebugAndroidTestKotlin`, dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockDCollectionSyncTest`
Expected: 2 Tests grün.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockDCollectionSyncTest.kt
git commit -m "test(ci): Block D — Multi-Source-Collection-Sync (D15 cross-source, D16 removeSource)"
```

---

## Task 3: Block E — Lese-Fortschritt-Round-Trip über frische Verbindung

**Files:**
- Create: `app/src/androidTest/kotlin/com/komgareader/app/ci/BlockEProgressRoundTripTest.kt`

Das vom Nutzer beschriebene Szenario: Fortschritt auf „Gerät 1" → landet am Server → eine **frische
lokale DB** (= lokaler Stand weg / Server neu verbunden, selber User-Account) zieht ihn per Pull zurück.
Zwei `CiSourceStack` gegen denselben Server A modellieren das (jede hat ihre eigene in-memory-DB).

- [ ] **Step 1: Testklasse schreiben**

```kotlin
package com.komgareader.app.ci

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.komgareader.domain.model.ReadProgress
import com.komgareader.domain.source.SourceFilter
import com.komgareader.domain.source.SyncingSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec §9 Block E — Server-Round-Trip des Lese-Fortschritts über einen lokalen Wipe / Reconnect.
 * „Gerät 1" liest (push) → Server hält den Fortschritt → eine FRISCHE lokale DB („Gerät 2" /
 * neu verbundener selber Account) pullt ihn zurück. Beweist offline-first + server-seitige Persistenz.
 */
@RunWith(AndroidJUnit4::class)
class BlockEProgressRoundTripTest {

    /** Setzt eine bekannte, vom Default verschiedene Seite — damit „kam wirklich vom Server" eindeutig ist. */
    @Test fun fortschritt_ueberlebt_lokalen_wipe_via_server_pull() = runTest {
        // GERÄT 1: liest ein Manga-Buch bis zu einer mittleren Seite.
        val device1 = CiSourceStack()
        val targetPage: Int
        val bookRemoteId: String
        try {
            device1.register(CiKomga.A)
            val src1 = device1.activeSource.all().first()
            val series = src1.browse(0, SourceFilter()).items.first { it.title == CiFixtures.MANGA_SERIES }
            val book = src1.books(series.remoteId).first()
            bookRemoteId = book.remoteId
            val pages = src1.pages(bookRemoteId)
            targetPage = (pages.size / 2).coerceAtLeast(2)   // mittig, ≠ 1 (Default), ≠ 0
            (src1 as SyncingSource).pushProgress(
                bookRemoteId,
                ReadProgress(bookId = 0L, page = targetPage, totalPages = pages.size, updatedAt = 1_700_000_000_000L),
            )
        } finally {
            device1.close()   // lokaler Stand „weg"
        }

        // GERÄT 2: frische lokale DB, selber Server/User. Ohne je lokal gelesen zu haben, muss der
        // Server den Fortschritt zurückliefern.
        val device2 = CiSourceStack()
        try {
            device2.register(CiKomga.A)
            val src2 = device2.activeSource.all().first() as SyncingSource
            val pulled = src2.pullProgress(bookRemoteId)
            assertNotNull("Server muss den auf Gerät 1 gesetzten Fortschritt liefern", pulled)
            assertEquals("Gerät 2 muss exakt die auf Gerät 1 gelesene Seite vom Server bekommen", targetPage, pulled!!.page)
            assertTrue("Fortschritt darf nicht der Default (Seite 1) sein", pulled.page >= 2)
        } finally {
            device2.close()
        }
    }
}
```

> Implementer-Hinweis: pushProgress mutiert Server-State (bleibt im komga-c/a-Volume). Da Push+Pull
> denselben `bookRemoteId` nutzen, ist der Round-Trip deterministisch. Kein Cleanup nötig (Fortschritt
> wird beim nächsten Lauf überschrieben). Falls `pullProgress` `page` 0-basiert zurückgibt (statt
> 1-basiert wie gepusht), an die echte Semantik anpassen + vermerken — NICHT die Assertion aufweichen.

- [ ] **Step 2: Kompiliert + ausführen**

Run: compile, dann
`ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.komgareader.app.ci.BlockEProgressRoundTripTest`
Expected: 1 Test grün.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/kotlin/com/komgareader/app/ci/BlockEProgressRoundTripTest.kt
git commit -m "test(ci): Block E — Fortschritt-Round-Trip über lokalen Wipe (Server-Pull, Nutzer-Szenario)"
```

---

## Task 4: Gesamtlauf + Spec-Update

- [ ] **Step 1: Alle ci-Tests (Seam) zusammen**

Run: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=com.komgareader.app.ci`
Expected: bisherige 17 Seam + 6 UI + **3 neue** (D15/D16 + E-Round-Trip) = **26 ci-Tests grün**
(plus der separate `CollectionSyncLiveTest` gegen Dev-Komga, falls dessen 25600-Instanz läuft).

- [ ] **Step 2: Spec §9b Umsetzungsstand aktualisieren**

D15/D16 (multi-source) + E-Round-Trip als umgesetzt vermerken; D17 (Single-Server push/pull/LWW/
reconnect) als durch den gemergten `CollectionSyncLiveTest` abgedeckt notieren. Commit:
```bash
git add docs/superpowers/specs/2026-06-10-integration-test-suite-design.md
git commit -m "docs(spec): D15/D16 (multi-source) + E-Round-Trip umgesetzt; D17 via CollectionSyncLiveTest"
```

---

## Self-Review (Plan-Autor)

- **Ergänzt statt dupliziert:** der gemergte `CollectionSyncLiveTest` deckt Single-Server (Discovery/
  Push-Pull-LWW/Reconnect) — dieser Plan deckt die **Multi-Source**-Dimension (D15/D16), die nur die
  CI-Topologie (A+B) ermöglicht, plus den Nutzer-Wunsch-**Round-Trip** (Block E).
- **Invarianten gewahrt (Skill):** D16 prüft Disconnect=lokal-only (kein Server-Löschen) + multi-source
  behält andere Quelle; D15 prüft per-Quelle-Subset-Push (Member-Identität `(sourceId, remoteId)`).
- **DRY:** Collection-Wiring einmal in `CiSourceStack` (spiegelt Produktion/Live-Test).
- **Round-Trip = echtes Nutzer-Szenario:** zwei Stacks/DBs gegen einen Server = „lokal weg, Server mappt
  zurück" — deterministisch über eine bekannte Nicht-Default-Seite.
- **Offene Annahmen:** `pullProgress`-Seiten-Basis (1- vs 0-basiert), `allCollectionSources`-Reihenfolge
  (name-basiert umgangen) — Implementer verifiziert beim ersten Lauf.

## Danach offen

- Optional: UI-level-Round-Trip (im Reader lesen → schließen → Settings disconnect → reconnect → Buch neu
  öffnen → Resume-Seite). Schwerer/brittler; der Seam-Round-Trip beweist die Server-Seite robust.
- Block H (Plugins `[pending]`), C10-UI (Webtoon), erster echter CI-Push (Runner-Registrierung).
