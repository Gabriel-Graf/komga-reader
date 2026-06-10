# Bidirektionaler Collections-Sync — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sammlungen werden bidirektional synchronisiert — Server-Sammlungen werden lokal entdeckt (Erstverbindung), Member-Änderungen per Last-Write-Wins (UTC) abgeglichen, am Server gelöschte Sammlungen mit Nutzer-Bestätigung lokal nachgezogen.

**Architecture:** Reine Domain-Funktion `planCollectionSync` entscheidet (anlegen/pushen/pull-überschreiben/verschwunden); `CollectionSyncManager.fullSync()` ist die dünne I/O-Shell, die Quellen agnostisch über `ActiveSource` listet und den Plan ausführt; `CollectionsViewModel` triggert geräteklassen-gegated und zeigt ein `EinkModal` für Löschungen.

**Tech Stack:** Kotlin, Jetpack Compose, Room, Hilt, Retrofit/kotlinx.serialization, JUnit. `java.time` für UTC-Parsing.

**Spec:** `docs/superpowers/specs/2026-06-10-collections-bidirectional-sync-design.md`

---

## Dateien-Übersicht

| Datei | Verantwortung | Aktion |
|---|---|---|
| `source-api/.../source/CollectionSyncSource.kt` | `RemoteCollection` + `updatedAt` | Modify |
| `source-komga/.../dto/CollectionDtos.kt` | `lastModifiedDate` in DTOs | Modify |
| `source-komga/.../KomgaMapper.kt` | `parseIsoUtcMillis` + Mapping | Modify |
| `source-komga/src/test/.../KomgaMapperCollectionTest.kt` | Mapper-/Parse-Tests | Create |
| `domain/.../repository/CollectionRepository.kt` | `CollectionSyncLink.updatedAt` | Modify |
| `data/.../repository/RoomCollectionRepository.kt` | `toLink`/`updateSyncLink` durchziehen | Modify |
| `domain/.../usecase/CollectionSyncPlan.kt` | Reiner Planner + Plan-Typen | Create |
| `domain/src/test/.../CollectionSyncPlanTest.kt` | Planner-Tests (TDD-Kern) | Create |
| `app/.../data/ActiveSource.kt` | `allCollectionSources()` | Modify |
| `app/.../data/CollectionSyncManager.kt` | `fullSync()`, `refresh()` raus | Modify |
| `app/src/test/.../CollectionSyncManagerTest.kt` | `fullSync`-Tests, `refresh`-Test raus | Modify |
| `app/.../ui/collections/SyncGating.kt` | reine Gating-Funktion | Create |
| `app/src/test/.../SyncGatingTest.kt` | Gating-Test | Create |
| `app/.../ui/collections/CollectionsViewModel.kt` | `fullSync`, vanished-Flow, `syncNow` umstellen | Modify |
| `app/.../ui/collections/CollectionsScreen.kt` | Vanished-`EinkModal`, Auto-Sync-Trigger | Modify |
| `app/.../i18n/Strings.kt` | neue Strings (de+en) | Modify |

---

## Task 1: `RemoteCollection.updatedAt` + UTC-Parsing im Komga-Mapper

**Files:**
- Modify: `source-api/src/main/kotlin/com/komgareader/domain/source/CollectionSyncSource.kt:5-10`
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/dto/CollectionDtos.kt`
- Modify: `source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaMapper.kt:103-107`
- Test: `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaMapperCollectionTest.kt`

- [ ] **Step 1: Test schreiben — UTC-Parsing mit/ohne Offset**

Create `source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaMapperCollectionTest.kt`:

```kotlin
package com.komgareader.source.komga

import com.komgareader.source.komga.dto.CollectionDto
import com.komgareader.source.komga.dto.ReadListDto
import org.junit.Assert.assertEquals
import org.junit.Test

class KomgaMapperCollectionTest {
    private val mapper = KomgaMapper()

    @Test fun `parseIsoUtcMillis liest Zulu-Zeit als UTC`() {
        // 2024-01-15T10:30:00Z = 1705314600000
        assertEquals(1705314600000L, mapper.parseIsoUtcMillis("2024-01-15T10:30:00Z"))
    }

    @Test fun `parseIsoUtcMillis ohne Offset wird als UTC interpretiert`() {
        // Komga liefert oft ohne Zone — muss GMT/UTC sein, nicht lokale Zone.
        assertEquals(1705314600000L, mapper.parseIsoUtcMillis("2024-01-15T10:30:00"))
    }

    @Test fun `parseIsoUtcMillis mit Offset rechnet auf UTC zurueck`() {
        // 12:30 +02:00 == 10:30 UTC == 1705314600000
        assertEquals(1705314600000L, mapper.parseIsoUtcMillis("2024-01-15T12:30:00+02:00"))
    }

    @Test fun `parseIsoUtcMillis bei leer gibt 0`() {
        assertEquals(0L, mapper.parseIsoUtcMillis(""))
    }

    @Test fun `toRemoteCollection mappt lastModifiedDate als updatedAt`() {
        val dto = CollectionDto(id = "c1", name = "X", seriesIds = listOf("s1"), lastModifiedDate = "2024-01-15T10:30:00Z")
        val rc = mapper.toRemoteCollection(dto)
        assertEquals("c1", rc.remoteId)
        assertEquals(listOf("s1"), rc.memberRemoteIds)
        assertEquals(1705314600000L, rc.updatedAt)
    }

    @Test fun `toRemoteCollection fuer ReadList mappt updatedAt`() {
        val dto = ReadListDto(id = "r1", name = "Y", bookIds = listOf("b1"), lastModifiedDate = "2024-01-15T10:30:00Z")
        val rc = mapper.toRemoteCollection(dto)
        assertEquals(1705314600000L, rc.updatedAt)
    }
}
```

- [ ] **Step 2: Test laufen lassen — kompiliert nicht (Felder fehlen)**

Run: `./gradlew :source-komga:testDebugUnitTest --tests "*KomgaMapperCollectionTest*"`
Expected: Compile-Fehler — `lastModifiedDate`, `parseIsoUtcMillis`, `RemoteCollection.updatedAt` existieren nicht.

- [ ] **Step 3: `RemoteCollection` um `updatedAt` erweitern**

In `source-api/.../source/CollectionSyncSource.kt`:

```kotlin
data class RemoteCollection(
    val remoteId: String,
    val name: String,
    val memberRemoteIds: List<String>,
    val updatedAt: Long,   // UTC epoch millis (GMT), niemals zonenbehaftet
)
```

- [ ] **Step 4: DTOs um `lastModifiedDate` ergänzen**

In `source-komga/.../dto/CollectionDtos.kt`, `CollectionDto` und `ReadListDto` je ergänzen:

```kotlin
@Serializable
data class CollectionDto(
    val id: String,
    val name: String,
    val ordered: Boolean = false,
    val seriesIds: List<String> = emptyList(),
    val lastModifiedDate: String = "",
)
```
```kotlin
@Serializable
data class ReadListDto(
    val id: String,
    val name: String,
    val summary: String = "",
    val ordered: Boolean = true,
    val bookIds: List<String> = emptyList(),
    val lastModifiedDate: String = "",
)
```

- [ ] **Step 5: Mapper — `parseIsoUtcMillis` + Mapping**

In `source-komga/.../KomgaMapper.kt`: oben die Imports ergänzen
```kotlin
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
```
und die `toRemoteCollection`-Funktionen ersetzen + Helper hinzufügen:

```kotlin
fun toRemoteCollection(dto: CollectionDto) =
    RemoteCollection(dto.id, dto.name, dto.seriesIds, parseIsoUtcMillis(dto.lastModifiedDate))

fun toRemoteCollection(dto: ReadListDto) =
    RemoteCollection(dto.id, dto.name, dto.bookIds, parseIsoUtcMillis(dto.lastModifiedDate))

/**
 * Komga-Zeitstempel → UTC epoch millis. Komga liefert `lastModifiedDate` mal mit Offset/`Z`,
 * mal OHNE Zone (dann ist UTC gemeint, NICHT die lokale Zone) — daher der UTC-Fallback.
 */
fun parseIsoUtcMillis(s: String): Long {
    if (s.isBlank()) return 0L
    return runCatching { Instant.parse(s).toEpochMilli() }
        .recoverCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }
        .recoverCatching { LocalDateTime.parse(s).toInstant(ZoneOffset.UTC).toEpochMilli() }
        .getOrDefault(0L)
}
```

- [ ] **Step 6: Test laufen lassen — grün**

Run: `./gradlew :source-komga:testDebugUnitTest --tests "*KomgaMapperCollectionTest*"`
Expected: PASS (6 Tests).

- [ ] **Step 7: Commit**

```bash
git add source-api/src/main/kotlin/com/komgareader/domain/source/CollectionSyncSource.kt source-komga/src/main/kotlin/com/komgareader/source/komga/dto/CollectionDtos.kt source-komga/src/main/kotlin/com/komgareader/source/komga/KomgaMapper.kt source-komga/src/test/kotlin/com/komgareader/source/komga/KomgaMapperCollectionTest.kt
git commit -m "feat(source-api): RemoteCollection.updatedAt (UTC) + Komga lastModifiedDate-Mapping"
```

---

## Task 2: `CollectionSyncLink.updatedAt` durch Domain + Room ziehen

**Files:**
- Modify: `domain/src/main/kotlin/com/komgareader/domain/repository/CollectionRepository.kt:10-16`
- Modify: `data/src/main/kotlin/com/komgareader/data/repository/RoomCollectionRepository.kt:86-118`
- Modify: `app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt:86-88` (writeLink-Signatur)

> Kontext: Die Entity `collection_sync_links` hat `updatedAt` bereits; nur das Domain-Modell und die Mapper ignorieren es. `updateSyncLink` überschreibt es heute fix mit `nowMillis()` — das muss den übergebenen Wert respektieren, damit ein Pull den Server-Zeitstempel persistieren kann.

- [ ] **Step 1: Domain-Modell erweitern**

In `domain/.../repository/CollectionRepository.kt`:

```kotlin
data class CollectionSyncLink(
    val collectionId: Long,
    val sourceId: Long,
    val remoteCollectionId: String?,
    val status: SyncStatus,
    val dirty: Boolean,
    val updatedAt: Long,   // UTC epoch millis: lokale Änderungszeit bzw. zuletzt abgeglichener Server-Stand
)
```

- [ ] **Step 2: Room-Mapping durchziehen**

In `data/.../RoomCollectionRepository.kt`:

`toLink` (Zeile ~112) ergänzen:
```kotlin
private fun toLink(e: CollectionSyncLinkEntity) = CollectionSyncLink(
    collectionId = e.collectionId,
    sourceId = e.sourceId,
    remoteCollectionId = e.remoteCollectionId,
    status = SyncStatus.valueOf(e.status),
    dirty = e.dirty,
    updatedAt = e.updatedAt,
)
```

`updateSyncLink` (Zeile ~86) — `updatedAt` aus dem Link persistieren statt `nowMillis()`:
```kotlin
override suspend fun updateSyncLink(link: CollectionSyncLink) {
    dao.upsertLink(
        CollectionSyncLinkEntity(
            collectionId = link.collectionId,
            sourceId = link.sourceId,
            remoteCollectionId = link.remoteCollectionId,
            status = link.status.name,
            dirty = link.dirty,
            updatedAt = link.updatedAt,
        ),
    )
}
```

- [ ] **Step 3: `writeLink` im Manager an die neue Signatur anpassen (Build grün halten)**

In `app/.../data/CollectionSyncManager.kt` die private `writeLink` um `updatedAt` (Default = jetzt) erweitern, damit die bestehenden `push()`-Aufrufer unverändert kompilieren:

```kotlin
private suspend fun writeLink(
    collectionId: Long,
    sourceId: Long,
    remoteId: String?,
    status: SyncStatus,
    dirty: Boolean,
    updatedAt: Long = System.currentTimeMillis(),
) {
    repo.updateSyncLink(CollectionSyncLink(collectionId, sourceId, remoteId, status, dirty, updatedAt))
}
```

- [ ] **Step 4: Build + bestehende Tests**

Run: `./gradlew :domain:test :data:testDebugUnitTest :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL (bestehende `CollectionSyncManagerTest` kompiliert noch — `refresh` bleibt vorerst).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/repository/CollectionRepository.kt data/src/main/kotlin/com/komgareader/data/repository/RoomCollectionRepository.kt app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt
git commit -m "feat(domain): CollectionSyncLink.updatedAt exponieren + in Room durchziehen"
```

---

## Task 3: Reiner Planner `planCollectionSync` (TDD-Kern)

**Files:**
- Create: `domain/src/main/kotlin/com/komgareader/domain/usecase/CollectionSyncPlan.kt`
- Test: `domain/src/test/kotlin/com/komgareader/domain/usecase/CollectionSyncPlanTest.kt`

- [ ] **Step 1: Tests schreiben (alle Pfade)**

Create `domain/src/test/kotlin/com/komgareader/domain/usecase/CollectionSyncPlanTest.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.CollectionMember
import com.komgareader.domain.model.SyncStatus
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.source.RemoteCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionSyncPlanTest {

    private fun link(colId: Long, srcId: Long, remoteId: String?, dirty: Boolean, updatedAt: Long) =
        CollectionSyncLink(colId, srcId, remoteId, if (dirty) SyncStatus.DIRTY else SyncStatus.SYNCED, dirty, updatedAt)

    private fun col(id: Long, name: String, members: List<CollectionMember>) =
        UserCollection(id, name, CollectionKind.SERIES, members)

    private fun member(srcId: Long, remoteId: String) = CollectionMember(srcId, remoteId, remoteId)

    @Test fun `Server hat Sammlung lokal fehlt - createLocal (Erstverbindung)`() {
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1", "s2"), updatedAt = 100)
        val plan = planCollectionSync(
            local = emptyList(),
            links = emptyMap(),
            remotePerSource = mapOf(7L to listOf(remote)),
        )
        assertEquals(1, plan.createLocal.size)
        assertEquals("rc1", plan.createLocal.first().remote.remoteId)
        assertEquals(7L, plan.createLocal.first().sourceId)
        assertTrue(plan.pushLocal.isEmpty())
        assertTrue(plan.pullOverwrite.isEmpty())
        assertTrue(plan.vanished.isEmpty())
    }

    @Test fun `beide vorhanden Server neuer - pullOverwrite`() {
        val local = col(1, "Marvel", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = false, updatedAt = 50)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1", "s2"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)))
        assertEquals(1, plan.pullOverwrite.size)
        assertEquals(listOf("s1", "s2"), plan.pullOverwrite.first().serverMemberRemoteIds)
        assertEquals(100L, plan.pullOverwrite.first().serverUpdatedAt)
        assertTrue(plan.pushLocal.isEmpty())
    }

    @Test fun `beide vorhanden lokal neuer - pushLocal`() {
        val local = col(1, "Marvel", listOf(member(7, "s1"), member(7, "s9")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = true, updatedAt = 200)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)))
        assertEquals(listOf(1L), plan.pushLocal)
        assertTrue(plan.pullOverwrite.isEmpty())
    }

    @Test fun `gleicher Zeitstempel - pushLocal (Tie-Break lokal)`() {
        val local = col(1, "Marvel", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = false, updatedAt = 100)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)))
        assertEquals(listOf(1L), plan.pushLocal)
    }

    @Test fun `nur lokal nie synced - pushLocal (anlegen)`() {
        val local = col(1, "Privat", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, remoteId = null, dirty = true, updatedAt = 200)))
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to emptyList()))
        assertEquals(listOf(1L), plan.pushLocal)
        assertTrue(plan.vanished.isEmpty())
    }

    @Test fun `war synced am Server weg - vanished`() {
        val local = col(1, "Weg", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, "rc1", dirty = false, updatedAt = 50)))
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to emptyList()))
        assertEquals(1, plan.vanished.size)
        assertEquals(1L, plan.vanished.first().collectionId)
        assertEquals("Weg", plan.vanished.first().name)
    }

    @Test fun `Match per Name wenn remoteId noch unbekannt`() {
        // Lokal nie gepusht (remoteId null), aber Server hat gleichnamige Sammlung -> adopt = pushLocal, kein Duplikat-create.
        val local = col(1, "Marvel", listOf(member(7, "s1")))
        val links = mapOf(1L to listOf(link(1, 7, remoteId = null, dirty = true, updatedAt = 200)))
        val remote = RemoteCollection("rc1", "Marvel", listOf("s1"), updatedAt = 100)
        val plan = planCollectionSync(listOf(local), links, mapOf(7L to listOf(remote)))
        assertEquals(listOf(1L), plan.pushLocal)
        assertTrue("kein Duplikat anlegen", plan.createLocal.isEmpty())
    }

    @Test fun `Multi-Source - eine Quelle pullt andere pusht`() {
        val local = col(1, "Misch", listOf(member(7, "a1"), member(8, "b1")))
        val links = mapOf(
            1L to listOf(
                link(1, 7, "rc7", dirty = false, updatedAt = 50),   // Server 7 neuer -> pull
                link(1, 8, "rc8", dirty = true, updatedAt = 300),   // lokal 8 neuer -> push
            ),
        )
        val remotePerSource = mapOf(
            7L to listOf(RemoteCollection("rc7", "Misch", listOf("a1", "a2"), updatedAt = 100)),
            8L to listOf(RemoteCollection("rc8", "Misch", listOf("b1"), updatedAt = 100)),
        )
        val plan = planCollectionSync(listOf(local), links, remotePerSource)
        assertEquals(1, plan.pullOverwrite.size)
        assertEquals(7L, plan.pullOverwrite.first().sourceId)
        assertEquals(listOf(1L), plan.pushLocal)
    }
}
```

- [ ] **Step 2: Tests laufen — fehlschlagen (Funktion fehlt)**

Run: `./gradlew :domain:test --tests "*CollectionSyncPlanTest*"`
Expected: Compile-Fehler — `planCollectionSync`, `SyncPlan` etc. existieren nicht.

- [ ] **Step 3: Planner implementieren**

Create `domain/src/main/kotlin/com/komgareader/domain/usecase/CollectionSyncPlan.kt`:

```kotlin
package com.komgareader.domain.usecase

import com.komgareader.domain.model.CollectionKind
import com.komgareader.domain.model.UserCollection
import com.komgareader.domain.repository.CollectionSyncLink
import com.komgareader.domain.source.RemoteCollection

/** Eine am Server entdeckte, lokal noch fehlende Sammlung (Discovery / Pull). */
data class DiscoveredCollection(
    val sourceId: Long,
    val kind: CollectionKind,
    val remote: RemoteCollection,
)

/** Server gewinnt für genau diese (Sammlung, Quelle): deren Subset überschreibt lokal. */
data class PullOverwrite(
    val collectionId: Long,
    val sourceId: Long,
    val serverMemberRemoteIds: List<String>,
    val remoteId: String,        // gematchte Server-remoteId → in den Link zurückschreiben
    val serverUpdatedAt: Long,   // Server-lastModified (UTC) → nach dem Pull in den Link schreiben
)

/** Lokal vorhanden, war schon synced, am Server jetzt weg → Nutzer-Bestätigung nötig. */
data class VanishedCollection(
    val collectionId: Long,
    val name: String,
)

data class SyncPlan(
    val createLocal: List<DiscoveredCollection>,
    val pushLocal: List<Long>,
    val pullOverwrite: List<PullOverwrite>,
    val vanished: List<VanishedCollection>,
)

/**
 * Reiner Sync-Planer (Last-Write-Wins per UTC-Zeitstempel). Pro (Sammlung, Quelle)-Link:
 *  - beide vorhanden: Server `updatedAt` > lokaler `link.updatedAt` → pull, sonst push.
 *  - nur Server (kein lokaler Match per remoteId/Name): createLocal (Discovery).
 *  - nur lokal, nie synced: push (am Server anlegen).
 *  - nur lokal, war synced, am Server weg: vanished.
 *
 * @param remotePerSource pro Quelle die zusammengeführte Server-Liste (SERIES + BOOK). Match über
 *  remoteId/Name, die je Quelle eindeutig sind.
 */
fun planCollectionSync(
    local: List<UserCollection>,
    links: Map<Long, List<CollectionSyncLink>>,
    remotePerSource: Map<Long, List<RemoteCollection>>,
): SyncPlan {
    val createLocal = mutableListOf<DiscoveredCollection>()
    val pushLocal = mutableSetOf<Long>()
    val pullOverwrite = mutableListOf<PullOverwrite>()
    val vanished = mutableListOf<VanishedCollection>()

    // remoteIds + Namen, die durch lokale Sammlungen „belegt" sind — pro Quelle.
    val matchedRemoteIds = mutableMapOf<Long, MutableSet<String>>()

    for (collection in local) {
        val colLinks = links[collection.id].orEmpty()
        // Quellen dieser Sammlung = Quellen mit Mitgliedern ODER mit existierendem Link.
        val sourceIds = (collection.members.map { it.sourceId } + colLinks.map { it.sourceId }).toSet()
        for (sourceId in sourceIds) {
            val remotes = remotePerSource[sourceId] ?: continue   // Quelle nicht sync-fähig/offline → unangetastet
            val link = colLinks.firstOrNull { it.sourceId == sourceId }
            val match = remotes.firstOrNull { it.remoteId == link?.remoteCollectionId }
                ?: remotes.firstOrNull { it.name == collection.name }

            if (match != null) {
                matchedRemoteIds.getOrPut(sourceId) { mutableSetOf() } += match.remoteId
                val localUpdated = link?.updatedAt ?: Long.MIN_VALUE
                if (match.updatedAt > localUpdated) {
                    pullOverwrite += PullOverwrite(collection.id, sourceId, match.memberRemoteIds, match.remoteId, match.updatedAt)
                } else {
                    pushLocal += collection.id
                }
            } else {
                // kein Server-Gegenstück
                if (link?.remoteCollectionId != null) {
                    vanished += VanishedCollection(collection.id, collection.name)
                } else {
                    pushLocal += collection.id   // nur lokal, nie synced → anlegen
                }
            }
        }
    }

    // Server-Sammlungen ohne lokalen Match → discovern.
    for ((sourceId, remotes) in remotePerSource) {
        val matched = matchedRemoteIds[sourceId].orEmpty()
        for (remote in remotes) {
            if (remote.remoteId !in matched) {
                createLocal += DiscoveredCollection(sourceId, CollectionKind.SERIES, remote)
            }
        }
    }

    return SyncPlan(
        createLocal = createLocal,
        pushLocal = pushLocal.toList(),
        pullOverwrite = pullOverwrite,
        vanished = vanished.distinctBy { it.collectionId },
    )
}
```

> Hinweis zur `kind`-Zuordnung beim Discovery: `RemoteCollection` trägt kein `kind`. Die Shell
> (Task 4) ruft `planCollectionSync` **getrennt je kind** auf (einmal mit den SERIES-Listen, einmal
> mit den BOOK-Listen) und überschreibt das hier gesetzte Default-`SERIES` beim Ausführen mit dem
> tatsächlichen kind des Aufrufs. So bleibt der Planner ohne kind-Wissen pur.

- [ ] **Step 4: Tests laufen — grün**

Run: `./gradlew :domain:test --tests "*CollectionSyncPlanTest*"`
Expected: PASS (8 Tests). Die Multi-Source- und Tie-Break-Tests laufen mit kind=SERIES; das ist für den Planer korrekt.

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/kotlin/com/komgareader/domain/usecase/CollectionSyncPlan.kt domain/src/test/kotlin/com/komgareader/domain/usecase/CollectionSyncPlanTest.kt
git commit -m "feat(domain): reiner planCollectionSync (LWW UTC, Discovery, Vanish)"
```

---

## Task 4: `ActiveSource.allCollectionSources()` + `CollectionSyncManager.fullSync()`

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/data/ActiveSource.kt:44-50`
- Modify: `app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt`
- Modify: `app/src/test/kotlin/com/komgareader/app/data/CollectionSyncManagerTest.kt`

- [ ] **Step 1: `ActiveSource` — alle Collection-Quellen agnostisch aufzählen**

In `app/.../data/ActiveSource.kt` nach `collectionSource(...)` hinzufügen:

```kotlin
/** Alle aktiven, schreibfähigen Collection-Quellen mit ihrer sourceId (für den Voll-Sync). */
open suspend fun allCollectionSources(): List<Pair<Long, CollectionSyncSource>> {
    val ids = syncAll()
    return ids.mapNotNull { id -> (sources.get(id) as? CollectionSyncSource)?.let { id to it } }
}
```

- [ ] **Step 2: Test schreiben — `fullSync` führt gemischten Plan aus**

In `app/src/test/.../CollectionSyncManagerTest.kt` einen Fake erweitern/ergänzen. Test (an bestehenden Stil/Fakes der Datei anlehnen — `FakeCollectionRepository` + `FakeCollectionSyncSource` existieren dort bereits aus den push-Tests):

```kotlin
@Test fun `fullSync entdeckt Server-Sammlung lokal und gibt vanished zurueck`() = runTest {
    // Server-Quelle 7: hat "Neu" (lokal unbekannt) -> createLocal.
    val source = FakeCollectionSyncSource(
        canWrite = true,
        series = listOf(RemoteCollection("rc-neu", "Neu", listOf("s1"), updatedAt = 100)),
    )
    val repo = FakeCollectionRepository()
    // lokal: "Weg" war mit rc-weg synced, Server hat sie nicht mehr -> vanished.
    val wegId = repo.seed(UserCollection(1, "Weg", CollectionKind.SERIES, listOf(CollectionMember(7, "x", "x"))))
    repo.seedLink(CollectionSyncLink(wegId, 7, "rc-weg", SyncStatus.SYNCED, dirty = false, updatedAt = 50))

    val manager = CollectionSyncManager(repo, resolver = { source }, allSources = { listOf(7L to source) })
    val vanished = manager.fullSync()

    assertTrue("Neu lokal angelegt", repo.collectionsNow().any { it.name == "Neu" })
    assertEquals(1, vanished.size)
    assertEquals("Weg", vanished.first().name)
}
```

> Falls die vorhandenen Fakes die Methoden `seed`/`seedLink`/`collectionsNow` nicht haben, im Test-File minimal ergänzen (sie sind Test-Doubles, keine Prod-API). `FakeCollectionSyncSource.listCollections(kind)` muss `series` für SERIES und eine `books`-Liste (Default leer) für BOOK liefern, `updatedAt` aus den RemoteCollection-Objekten.

- [ ] **Step 3: Test laufen — fehlschlägt**

Run: `./gradlew :app:testDebugUnitTest --tests "*CollectionSyncManagerTest*"`
Expected: Compile-Fehler — `fullSync` und der `allSources`-Konstruktorparameter fehlen.

- [ ] **Step 4: `CollectionSyncManager` — Konstruktor + `fullSync`, `refresh` entfernen**

In `app/.../data/CollectionSyncManager.kt`:

Konstruktor um `allSources` erweitern; Hilt-Konstruktor anpassen:
```kotlin
@Singleton
class CollectionSyncManager(
    private val repo: CollectionRepository,
    private val resolver: suspend (sourceId: Long) -> CollectionSyncSource?,
    private val allSources: suspend () -> List<Pair<Long, CollectionSyncSource>>,
) {
    @Inject constructor(repo: CollectionRepository, active: ActiveSource) :
        this(repo, { id -> active.collectionSource(id) }, { active.allCollectionSources() })
```

`refresh(...)` löschen. `fullSync()` hinzufügen (nutzt `push`, `planCollectionSync`, `mergeSubsets`):
```kotlin
/**
 * Voller bidirektionaler Sync: Server-Sammlungen je Quelle listen, Plan rechnen, ausführen.
 * Gibt am Server verschwundene (früher synchrone) Sammlungen zurück — die UI bestätigt deren
 * lokale Löschung. Discovery + Pull laufen stumm.
 */
suspend fun fullSync(): List<VanishedCollection> {
    val collections = repo.collections.first()
    val links = collections.associate { it.id to repo.syncLinks(it.id).first() }
    val srcs = allSources()

    val vanished = mutableListOf<VanishedCollection>()
    for (kind in CollectionKind.values()) {
        val remotePerSource = srcs.associate { (id, src) ->
            id to runCatching { src.listCollections(kind) }.getOrDefault(emptyList())
        }
        val kindCollections = collections.filter { it.kind == kind }
        val plan = planCollectionSync(
            local = kindCollections,
            links = links.filterKeys { id -> kindCollections.any { it.id == id } },
            remotePerSource = remotePerSource,
        )
        executePlan(plan, kind)
        vanished += plan.vanished
    }
    return vanished.distinctBy { it.collectionId }
}

private suspend fun executePlan(plan: SyncPlan, kind: CollectionKind) {
    // 1) Discovery: Server-Sammlung lokal anlegen.
    for (d in plan.createLocal) {
        val newId = repo.create(d.remote.name, kind)
        val members = d.remote.memberRemoteIds.map { CollectionMember(d.sourceId, it, it) }
        repo.setMembers(newId, members)   // markiert Link DIRTY/remoteId=null …
        writeLink(newId, d.sourceId, d.remote.remoteId, SyncStatus.SYNCED, dirty = false, updatedAt = d.remote.updatedAt)
    }
    // 2) Pull-Overwrite: Server-Subset gewinnt für diese Quelle.
    for (p in plan.pullOverwrite) {
        val current = repo.get(p.collectionId) ?: continue
        val merged = mergeSubsets(current.members, mapOf(p.sourceId to p.serverMemberRemoteIds)) { _, rid ->
            current.members.firstOrNull { it.sourceId == p.sourceId && it.remoteId == rid }?.title ?: rid
        }
        repo.setMembers(p.collectionId, merged)   // … nullt den Link → danach mit Server-Stand korrigieren:
        val remoteId = repo.syncLinks(p.collectionId).first()
            .firstOrNull { it.sourceId == p.sourceId }?.remoteCollectionId
        writeLink(p.collectionId, p.sourceId, remoteId, SyncStatus.SYNCED, dirty = false, updatedAt = p.serverUpdatedAt)
    }
    // 3) Push: lokaler Stand zum Server (best-effort, unverändert).
    for (id in plan.pushLocal) {
        repo.get(id)?.let { push(it) }
    }
    // vanished: NICHT automatisch löschen — die UI bestätigt.
}
```

> Hinweis: `repo.setMembers` setzt den Link auf `remoteCollectionId = null`. Im Pull-Pfad lesen wir
> den ursprünglichen `remoteCollectionId` vor dem Überschreiben **nicht** verloren, weil `writeLink`
> ihn direkt danach wieder schreibt — der Zwischenschritt liest ihn aus dem frischen `syncLinks`-Flow,
> der nach `setMembers` zwar `null` enthielte; daher den `remoteCollectionId` **vor** `setMembers`
> aus dem Plan-Kontext halten. **Sauberere Variante (verbindlich umsetzen):** `PullOverwrite` trägt
> zusätzlich `remoteId: String` (der gematchte Server-`remoteId`); im Planner `match.remoteId`
> mitgeben, hier `p.remoteId` statt des Re-Reads verwenden. Dann entfällt der `syncLinks`-Re-Read komplett.

> **Umsetzungs-Entscheidung (verbindlich):** `PullOverwrite` trägt **zwei** zusätzliche Felder
> `serverUpdatedAt: Long` **und** `remoteId: String`. Der Planner füllt beide aus `match`. `executePlan`
> nutzt `p.remoteId` + `p.serverUpdatedAt` direkt — **kein** `syncLinks`-Re-Read. Der `pullOverwrite`-Test
> in Task 3 prüft `serverUpdatedAt` (und optional `remoteId`) mit.

Nötige Imports ergänzen: `CollectionKind`, `CollectionMember`, `planCollectionSync`, `SyncPlan`, `VanishedCollection`, `kotlinx.coroutines.flow.first`.

- [ ] **Step 5: `refresh`-Test entfernen**

In `CollectionSyncManagerTest.kt` den/die `refresh`-Test(s) löschen (Szenarien sind in `CollectionSyncPlanTest` abgedeckt).

- [ ] **Step 6: Tests laufen — grün**

Run: `./gradlew :app:testDebugUnitTest --tests "*CollectionSyncManagerTest*"`
Expected: PASS (inkl. neuem `fullSync`-Test; keine `refresh`-Referenz mehr).

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/data/ActiveSource.kt app/src/main/kotlin/com/komgareader/app/data/CollectionSyncManager.kt app/src/test/kotlin/com/komgareader/app/data/CollectionSyncManagerTest.kt domain/src/main/kotlin/com/komgareader/domain/usecase/CollectionSyncPlan.kt domain/src/test/kotlin/com/komgareader/domain/usecase/CollectionSyncPlanTest.kt
git commit -m "feat(app): CollectionSyncManager.fullSync (bidirektional) + ActiveSource.allCollectionSources; refresh entfernt"
```

---

## Task 5: Gating-Funktion + `CollectionsViewModel` verdrahten

**Files:**
- Create: `app/src/main/kotlin/com/komgareader/app/ui/collections/SyncGating.kt`
- Test: `app/src/test/kotlin/com/komgareader/app/ui/collections/SyncGatingTest.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsViewModel.kt`

- [ ] **Step 1: Test für die reine Gating-Funktion**

Create `app/src/test/kotlin/com/komgareader/app/ui/collections/SyncGatingTest.kt`:

```kotlin
package com.komgareader.app.ui.collections

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncGatingTest {
    @Test fun `EINK erlaubt keinen aggressiven Sync`() {
        assertFalse(aggressiveSyncAllowed("EINK"))
    }
    @Test fun `SMARTPHONE erlaubt aggressiven Sync`() {
        assertTrue(aggressiveSyncAllowed("SMARTPHONE"))
    }
}
```

- [ ] **Step 2: Test laufen — fehlschlägt**

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncGatingTest*"`
Expected: Compile-Fehler — `aggressiveSyncAllowed` fehlt.

- [ ] **Step 3: Gating-Funktion**

Create `app/src/main/kotlin/com/komgareader/app/ui/collections/SyncGating.kt`:

```kotlin
package com.komgareader.app.ui.collections

/**
 * Darf bei jedem Tab-Öffnen voll gesynct werden? Nur auf bewegungs-/akku-unkritischen Geräten
 * (LCD/Smartphone). Auf E-Ink läuft der Voll-Sync nur an Server-Connect/App-Start und manuell.
 * Bewusst keine binäre `isEink`-Zementierung: leitet aus dem Display-Mode ab, künftig aus
 * DisplayBehavior.allowsMotion.
 */
fun aggressiveSyncAllowed(displayMode: String): Boolean = displayMode != "EINK"
```

- [ ] **Step 4: Test laufen — grün**

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncGatingTest*"`
Expected: PASS (2 Tests).

- [ ] **Step 5: `CollectionsViewModel` — fullSync, vanished-Flow, Trigger, syncNow umstellen**

In `app/.../ui/collections/CollectionsViewModel.kt`:

Imports ergänzen:
```kotlin
import com.komgareader.domain.usecase.VanishedCollection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
```

Felder + Init (nach `viewMode`):
```kotlin
private val _vanished = MutableStateFlow<List<VanishedCollection>>(emptyList())
val vanished: StateFlow<List<VanishedCollection>> = _vanished.asStateFlow()

private var autoSyncedOnce = false

init {
    // App-Start / Server-Connect: einmal pro VM-Leben voll synchronisieren (push + pull).
    syncOnceOnEnter()
}

/** Einmaliger Auto-Sync beim ersten Sichtbarwerden (Recompositions lösen keinen Sturm aus). */
fun syncOnceOnEnter() {
    if (autoSyncedOnce) return
    autoSyncedOnce = true
    fullSync()
}

/** Tab-Öffnen: nur auf Nicht-E-Ink zusätzlich voll synchronisieren (Akku-Schonung auf E-Ink). */
fun syncOnTabOpen() = viewModelScope.launch {
    if (aggressiveSyncAllowed(settings.displayMode.first())) fullSync()
}

private fun fullSync() = viewModelScope.launch {
    _vanished.value = sync.fullSync()
}

/** „Hier auch löschen": lokal entfernen (Server-Stand bleibt wie er ist — Sammlung ist dort weg). */
fun confirmVanishedDelete(ids: List<Long>) = viewModelScope.launch {
    ids.forEach { repo.delete(it) }
    _vanished.value = emptyList()
}

/** „Hier behalten": Modal schließen; Sammlung bleibt, nächster Push legt sie am Server neu an. */
fun dismissVanished() { _vanished.value = emptyList() }
```

`syncNow` auf bidirektional umstellen (Kommentar ersetzen):
```kotlin
// Nutzer-initiiertes „jetzt synchronisieren" = voller bidirektionaler Sync (push + pull).
fun syncNow(id: Long) = viewModelScope.launch {
    _vanished.value = sync.fullSync()
}
```

> `id` bleibt in der Signatur (Call-Sites unverändert), wird aber nicht mehr gebraucht — der Voll-Sync deckt alle Sammlungen ab. Falls der Compiler über den ungenutzten Parameter warnt: `@Suppress("UNUSED_PARAMETER")` ist hier **nicht** nötig (Kotlin warnt bei Funktionsparametern nicht als Fehler); Signatur bewusst stabil halten.

- [ ] **Step 6: Build + Tests**

Run: `./gradlew :app:testDebugUnitTest --tests "*SyncGatingTest*" :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/ui/collections/SyncGating.kt app/src/test/kotlin/com/komgareader/app/ui/collections/SyncGatingTest.kt app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsViewModel.kt
git commit -m "feat(collections): VM bidirektionaler fullSync + geräteklassen-gegateter Trigger + vanished-Flow"
```

---

## Task 6: Vanished-`EinkModal` + Auto-Sync-Trigger im Screen + i18n

**Files:**
- Modify: `app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt`
- Modify: `app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsScreen.kt`

- [ ] **Step 1: i18n-Strings ergänzen (de + en)**

In `app/.../i18n/Strings.kt` im Strings-Interface/-Datenklasse die Keys ergänzen und in **beiden** Sprach-Implementierungen füllen (dem bestehenden Muster der Datei folgen — echte Umlaute):

Keys:
```kotlin
val collectionVanishedTitle: String        // DE: "Am Server gelöscht"            EN: "Deleted on server"
val collectionVanishedBody: String         // DE: "Diese Sammlungen sind am Server nicht mehr vorhanden. Hier auch löschen?"  EN: "These collections no longer exist on the server. Delete them here too?"
val collectionVanishedDeleteHere: String   // DE: "Hier auch löschen"             EN: "Delete here too"
val collectionVanishedKeepHere: String     // DE: "Hier behalten"                 EN: "Keep here"
```

- [ ] **Step 2: Auto-Sync beim Betreten + Vanished-Modal im Screen**

In `app/.../ui/collections/CollectionsScreen.kt`:

Auto-Sync beim Sichtbarwerden — am Anfang des Screen-Composables (nach VM-Bezug):
```kotlin
LaunchedEffect(Unit) { viewModel.syncOnceOnEnter() }
```
(`syncOnceOnEnter` ist idempotent; zusätzlich kann `viewModel.syncOnTabOpen()` an die Tab-Auswahl in `HomeScreen` gehängt werden — optionaler Schritt, siehe Task 6a.)

Vanished-Modal — neben die bestehenden Dialoge (z. B. nach dem `deleting`-Block, vor `syncInfoFor`):
```kotlin
val vanished by viewModel.vanished.collectAsState()
if (vanished.isNotEmpty()) {
    EinkModal(
        title = s.collectionVanishedTitle,
        onDismiss = { viewModel.dismissVanished() },
        confirmLabel = s.collectionVanishedDeleteHere,
        onConfirm = { viewModel.confirmVanishedDelete(vanished.map { it.collectionId }) },
        dismissLabel = s.collectionVanishedKeepHere,
    ) {
        Text(s.collectionVanishedBody, style = MaterialTheme.typography.bodyMedium)
        vanished.forEach { v ->
            Text("• ${v.name}", style = MaterialTheme.typography.bodyLarge)
        }
    }
}
```

Imports sicherstellen: `androidx.compose.runtime.collectAsState`, `androidx.compose.runtime.getValue`, `androidx.compose.runtime.LaunchedEffect`, `com.komgareader.app.ui.components.EinkModal` (bereits genutzt).

- [ ] **Step 3: Build**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. (i18n hat Compile-Zeit-Parität — fehlt ein Key in einer Sprache, bricht der Build hier.)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/kotlin/com/komgareader/app/i18n/Strings.kt app/src/main/kotlin/com/komgareader/app/ui/collections/CollectionsScreen.kt
git commit -m "feat(collections): Vanished-Bestätigungs-Modal (EinkModal) + Auto-Sync beim Öffnen + i18n"
```

### Task 6a (optional, nur LCD-Politur): Tab-Open-Sync verdrahten

**Files:** Modify `app/src/main/kotlin/com/komgareader/app/ui/home/HomeScreen.kt:~261`

- [ ] An der Stelle, wo der Collections-Tab ausgewählt wird, zusätzlich `collectionsVm.syncOnTabOpen()` aufrufen (in einem `LaunchedEffect(selectedTab)`-Block, der nur beim Wechsel **auf** den Collections-Tab feuert). Auf E-Ink ist das durch `aggressiveSyncAllowed` ein No-Op. Build: `./gradlew :app:compileDebugKotlin`. Commit: `feat(collections): Tab-Open-Sync auf LCD verdrahtet`.

---

## Task 7: Verifikation (Build, Lint, E2E gegen lokale Test-Komga)

**Files:** keine (Verifikation).

- [ ] **Step 1: Voller Build + alle Unit-Tests**

Run: `./gradlew :domain:test :source-komga:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle grün.

- [ ] **Step 2: Pre-commit/Lint (falls konfiguriert)**

Run: `./gradlew ktlintCheck detekt` (oder die im Projekt etablierten Tasks; sonst überspringen).
Expected: keine Verstöße.

- [ ] **Step 3: E2E gegen lokale Test-Komga (siehe `local-test-komga` Memory)**

Manuell/Skript gegen die Docker-Komga:
1. **Discovery:** Am Server `POST /api/v1/collections` eine Sammlung anlegen, die lokal nicht existiert. App (frisch) öffnen → Collections-Tab → Sammlung erscheint lokal. **Beweis:** Screenshot.
2. **Push:** Lokal ein Werk zu einer Sammlung adden → `GET /api/v1/collections/{id}` zeigt das neue `seriesIds`-Mitglied.
3. **Pull/LWW:** Am Server die Sammlung ändern (Mitglied entfernen/hinzufügen), `lastModifiedDate` wird neuer → in der App „Sync" → lokale Mitgliederliste übernimmt den Server-Stand.
4. **Vanish:** Am Server die Sammlung löschen → in der App „Sync" → `EinkModal` listet sie → „Hier behalten" lässt sie lokal, „Hier auch löschen" entfernt sie.

- [ ] **Step 4: Emulator-Verifikation `eink_test` (1264×1680@300)**

App auf dem AVD starten, Schritte 1 + 4 visuell prüfen (Modal entspricht E-Ink-Designsprache: flach, Border, `AppIcons`, keine Animation).

- [ ] **Step 5: Doku nachziehen (docs-match-code)**

`.claude/rules/architecture-seams.md` (Naht A): vermerken, dass `RemoteCollection` jetzt `updatedAt` (UTC) trägt und Collections **bidirektional** (push+pull, LWW, Discovery, Vanish-Modal) synchronisieren. Im selben Commit wie der Feature-Abschluss.

```bash
git add .claude/rules/architecture-seams.md
git commit -m "docs(rules): Naht A — RemoteCollection.updatedAt + bidirektionaler Collections-Sync"
```

---

## Self-Review-Notizen (Plan ↔ Spec)

- **Spec-Abdeckung:** Naht-Erweiterung (T1), Domain-Link (T2), Planner inkl. aller fünf Pfade (T3), `fullSync`-Shell + Discovery/Pull/Push-Ausführung (T4), Geräteklassen-Gating + Trigger + vanished-Flow (T5), Modal + i18n (T6), E2E (T7). Alle Spec-Abschnitte haben einen Task.
- **`serverUpdatedAt`:** Verbindliche Entscheidung in T4-Step4 — `PullOverwrite` trägt `serverUpdatedAt`; T3 ist entsprechend zu implementieren (Feld + Planer gibt `match.updatedAt` mit). Der `pullOverwrite`-Test in T3 prüft das Feld mit.
- **Bekannte Grenze (Uhr-Drift):** dokumentiert in der Spec; LWW-Code vergleicht UTC-Millis — keine Zonen-Arithmetik. Kein offener Task.
- **Titel beim Discovery:** Mitglieder einer entdeckten Sammlung tragen vorerst `remoteId` als Titel (RemoteCollection liefert keine Titel). Bewusst, YAGNI — Cover/Titel werden beim Browsen über `sourceId`+`remoteId` aufgelöst.
</content>
